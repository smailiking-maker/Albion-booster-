#!/usr/bin/env bash
set -euo pipefail

# Запуск на чистом Ubuntu/Debian VPS в Европе (Амстердам / Франкфурт / Хельсинки):
# sudo bash setup-eu-node.sh 443

WG_PORT="${1:-51820}"
EXT_IF="$(ip route show default | awk '/default/ {print $5; exit}')"
PUB_IP="$(curl -fsSL https://api.ipify.org)"

apt-get update -y
apt-get install -y wireguard iptables

umask 077
SRV_PRIV="$(wg genkey)"
SRV_PUB="$(echo "$SRV_PRIV" | wg pubkey)"
CLI_PRIV="$(wg genkey)"
CLI_PUB="$(echo "$CLI_PRIV" | wg pubkey)"

cat > /etc/wireguard/wg0.conf <<EOF
[Interface]
Address = 10.66.66.1/24
ListenPort = ${WG_PORT}
PrivateKey = ${SRV_PRIV}
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${EXT_IF} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${EXT_IF} -j MASQUERADE

[Peer]
PublicKey = ${CLI_PUB}
AllowedIPs = 10.66.66.2/32
EOF

sysctl -w net.ipv4.ip_forward=1
grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf

systemctl enable --now wg-quick@wg0

cat > /root/albion-eu.conf <<EOF
[Interface]
PrivateKey = ${CLI_PRIV}
Address = 10.66.66.2/32
DNS = 1.1.1.1, 8.8.8.8
MTU = 1280

[Peer]
PublicKey = ${SRV_PUB}
Endpoint = ${PUB_IP}:${WG_PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
EOF

echo ""
echo "Готово! Файл конфига сохранён в /root/albion-eu.conf"
echo "Скопируйте текст ниже и вставьте в приложение Albion Boost:"
echo "--------------------------------------------------------"
cat /root/albion-eu.conf
echo "--------------------------------------------------------"
