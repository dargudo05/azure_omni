# Centralización de IP de Salida en Azure (SASF → Omni)

Este documento resume la arquitectura, comandos y validación realizados en Azure para centralizar el tráfico saliente (_egress_) de un servicio en **una única dirección IP pública fija y dedicada**.

## Datos del Entorno en Azure

| Recurso               | Valor                                                                 |
| :-------------------- | :-------------------------------------------------------------------- |
| **Grupo de recursos** | `democlaro`                                                           |
| **Región**            | `westus3`                                                             |
| **App Service**       | `sasf-emitor-lab` (Linux, Java 17, plan Basic B1)                     |
| **VNet / Subred**     | `vnet-sasf-lab` (`10.10.0.0/16`) / `snet-appservice` (`10.10.1.0/24`) |
| **NAT Gateway**       | `nat-sasf-egress`                                                     |
| **IP Pública Fija**   | `pip-sasf-egress` (`20.171.121.241`)                                  |

---

## 1. Problema Observado (Antes)

Por defecto, un Azure App Service (`sasf-emitor-lab` en `westus3`, grupo `democlaro`) origina sus peticiones salientes desde un **pool dinámico y compartido de 31 direcciones IPv4** (por ejemplo, `4.148.186.40`), lo cual impide a Omni mantener una lista blanca (_IP Whitelisting_) segura y estable.

---

## 2. Comandos Ejecutados en Azure y su Explicación

Ejecutados en **Azure Cloud Shell (Bash)** sobre el grupo de recursos `democlaro` en `westus3`:

```bash
az network public-ip create --resource-group democlaro --name pip-sasf-egress --location westus3 --sku Standard --allocation-method Static
```

- **Qué hace:** Crea una IP pública dedicada (`20.171.121.241`) con SKU `Standard` y asignación `Static`. No cambia al reiniciar ni escalar y es exclusiva de la suscripción.

```bash
az network vnet create -g democlaro -n vnet-sasf-lab -l westus3 --address-prefixes 10.10.0.0/16 --subnet-name snet-appservice --subnet-prefixes 10.10.1.0/24
```

- **Qué hace:** Crea la red virtual privada `vnet-sasf-lab` (`10.10.0.0/16`) y la subred `snet-appservice` (`10.10.1.0/24`).

```bash
az network vnet subnet update -g democlaro --vnet-name vnet-sasf-lab -n snet-appservice --delegations Microsoft.Web/serverFarms
```

- **Qué hace:** Delega la subred `snet-appservice` a `Microsoft.Web/serverFarms`, requisito de Azure para integrar un App Service a la subred.

```bash
az network nat gateway create -g democlaro -n nat-sasf-egress -l westus3 --public-ip-addresses pip-sasf-egress
```

- **Qué hace:** Crea el Azure NAT Gateway (`nat-sasf-egress`) y le asigna la IP pública fija `pip-sasf-egress`.

```bash
az network vnet subnet update -g democlaro --vnet-name vnet-sasf-lab -n snet-appservice --nat-gateway nat-sasf-egress
```

- **Qué hace:** Asocia el NAT Gateway a la subred `snet-appservice` para que todo el tráfico saliente de esa subred haga SNAT hacia `20.171.121.241`.

```bash
az webapp vnet-integration add -g democlaro -n sasf-emitor-lab --vnet vnet-sasf-lab --subnet snet-appservice
```

- **Qué hace:** Conecta el App Service `sasf-emitor-lab` (plan Basic B1) a la subred `snet-appservice`.

```bash
az webapp config set -g democlaro -n sasf-emitor-lab --vnet-route-all-enabled true
```

- **Qué hace:** Activa `Route All` (`vnetRouteAllEnabled = true`). **Paso crítico:** obliga a que el 100% del tráfico saliente (incluido internet público, no solo IPs privadas) pase por la VNet y salga por el NAT Gateway.

```bash
az network public-ip show -g democlaro -n pip-sasf-egress --query ipAddress -o tsv
```

- **Qué hace:** Consulta y confirma la IP pública fija asignada (`20.171.121.241`).

---

## 3. Validación y Resultados

| Escenario   | Enrutamiento                                        | IP detectada en destino                | Estado                           |
| :---------- | :-------------------------------------------------- | :------------------------------------- | :------------------------------- |
| **Antes**   | Salida por defecto de App Service (pool de 31 IPv4) | `4.148.186.40` (dinámica/compartida)   | `20/20 OK`                       |
| **Después** | VNet Integration + `Route All` + NAT Gateway        | **`20.171.121.241`** (fija y dedicada) | `10/10 OK` (`uniqueIpsCount: 1`) |

---

## 4. Conclusiones para Producción

1. **Escalable a múltiples servicios:** Cualquier otro servicio de SASF integrado a la misma VNet/subred con `Route All` saldrá automáticamente con la misma IP **`20.171.121.241`**.
2. **Lista blanca en Omni:** Omni solo debe autorizar la IP **`20.171.121.241`**.

---

## 5. Resumen Ejecutivo

Se conectó el App Service de Azure (`sasf-emitor-lab`) a una **red virtual (`VNet`)** con **`Route All`** habilitado y un **Azure NAT Gateway** asociado a una **IP pública estática (`20.171.121.241`)**. Con esto, todo el tráfico saliente dejó de usar el pool dinámico de 31 IPs compartidas de Azure y pasó a salir siempre por **una única IP fija y dedicada**, sin modificar el código de la aplicación.
