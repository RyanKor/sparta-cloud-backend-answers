#!/bin/bash
echo "Validating service..."

PORT=8080 # .service 파일에 정의된 포트
MAX_ATTEMPTS=10
SLEEP_TIME=5
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
  
  # 1. systemd 서비스가 'active' 상태인지 확인
  if systemctl is-active --quiet payment-system; then
    echo "Service is 'active'."
    
    # 2. (선택적이지만 강력히 권장) 서비스가 포트를 리스닝하고 있는지 확인
    if lsof -i :$PORT > /dev/null; then
      echo "Service is UP and listening on port $PORT."
      exit 0 # 성공
    else
      echo "Service is 'active' but not yet listening on port $PORT. Retrying..."
    fi
    
  else
    echo "Service is not yet 'active'. Retrying... (Attempt $(($ATTEMPT + 1))/$MAX_ATTEMPTS)"
  fi
  
  ATTEMPT=$(($ATTEMPT + 1))
  sleep $SLEEP_TIME
done

echo "Error: Service failed to start or validate after $MAX_ATTEMPTS attempts."

# 디버깅을 위해 서비스 상태 로그 출력
sudo systemctl status payment-system --no-pager -l
exit 1

