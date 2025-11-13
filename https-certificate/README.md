
``` bash
# https 반영하기 위한 caddy 사용 힌트

# 1. Caddy 설치 방법 (Amazon Linux 2023)
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable @caddy/caddy epel-9-x86_64
sudo dnf install caddy

# 2. Caddy에 HTTPS 인증서 반영하기 위한 작업하기 (여기를 수정해주세요!)
sudo vim /etc/caddy/Caddyfile

<여러분의 EC2 퍼블릭 IP>.nip.io {

        tls internal
        reverse_proxy localhost:8080

}

# 3. Caddy 리눅스에서 실행해주기
# Caddy 활성화를 위한 리눅스 커맨드를 찾아보세요!

sudo systemctl enable caddy
sudo systemctl start caddy

# status가 active이어야 정상 실행된 것입니다.
sudo systemctl status caddy

# 4. caddy 실행 이후, 인증서 발급 정보 확인하기
sudo journalctl -u caddy -n 100 --no-pager

# 5. 아래와 같은 메세지 결과가 나오면 성공!!
{"level":"info","ts":1763042276.6140883,"logger":"tls.obtain","msg":"certificate obtained successfully","identifier":"13.125.6.42.nip.io","issuer":"acme-v02.api.letsencrypt.org-directory"}

# 6. EC2에 https로 요청 보내보기

$ curl -v https://13.125.6.42.nip.io

# 응답 예상 결과
* Host 13.125.6.42.nip.io:443 was resolved.
* IPv6: (none)
* IPv4: 13.125.6.42
*   Trying 13.125.6.42:443...
* Connected to 13.125.6.42.nip.io (13.125.6.42) port 443
* ALPN: curl offers h2,http/1.1
* (304) (OUT), TLS handshake, Client hello (1):
*  CAfile: /etc/ssl/cert.pem
*  CApath: none
* (304) (IN), TLS handshake, Server hello (2):
* (304) (IN), TLS handshake, Unknown (8):
* (304) (IN), TLS handshake, Certificate (11):
* SSL certificate problem: unable to get local issuer certificate
* Closing connection
```