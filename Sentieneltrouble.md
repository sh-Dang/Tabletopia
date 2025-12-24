````md
# Redis Sentinel HA 트러블슈팅 정리

## 1. 목표
Docker Compose 환경에서 Redis **Master / Replica / Sentinel(3대)** 구성을 구축하고,  
Failover가 정상적으로 동작하는지 검증한다.

---

## 2. 구성 개요

### 2.1 아키텍처
- Redis Master 1대
- Redis Replica 1대
- Redis Sentinel 3대
- Docker bridge network 사용
- Redis 버전: 7.x (alpine)

### 2.2 통신 방식
- Docker 컨테이너 이름(hostname) 기반 통신
- IP 하드코딩 제거

---

## 3. Redis 설정

### 3.1 Redis 서버 공통 설정 (Master / Replica)

```conf
bind 0.0.0.0
protected-mode no
port 6379

requirepass yourpassword
masterauth yourpassword

replica-read-only yes
replica-priority 100
````

* AUTH 적용
* Replica는 masterauth 사용
* Docker 환경이므로 bind 0.0.0.0 필수

---

### 3.2 Sentinel 설정

```conf
port 26379
bind 0.0.0.0
protected-mode no

sentinel resolve-hostnames yes
sentinel announce-hostnames yes

sentinel monitor mymaster redis-master 6379 2
sentinel auth-pass mymaster yourpassword

sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

* hostname 기반 master 감시
* quorum = 2 (Sentinel 3대 중 2대 합의)

---

## 4. 트러블슈팅 이슈 및 해결

### 4.1 sentinel get-master-addr-by-name 결과가 nil

```bash
sentinel get-master-addr-by-name mymaster
(nil)
```

**원인**

* sentinel.conf가 디렉터리로 mount됨
* 설정 파일이 실제로 로드되지 않음

**해결**

```yaml
volumes:
  - ./sentinel/sentinel1.conf:/usr/local/etc/redis/sentinel.conf
```

---

### 4.2 Sentinel이 master hostname을 resolve하지 못함

```text
Failed to resolve hostname 'redis-master'
```

**원인**

* Sentinel 설정에서 IP와 hostname 혼용
* CONFIG REWRITE로 이전 IP 설정 잔존

**해결**

* Sentinel 설정 hostname 기반으로 통일
* `docker-compose down -v` 후 재기동

---

## 5. Failover 테스트

### 5.1 정상 상태 확인

```bash
redis-cli -p 26379 sentinel masters
redis-cli -p 26379 sentinel get-master-addr-by-name mymaster
```

결과:

```text
"redis-master" "6379"
```

---

### 5.2 Master 강제 종료

```bash
docker stop redis-master
```

---

### 5.3 Sentinel 로그 핵심 이벤트

```text
+sdown master mymaster
+odown master mymaster #quorum 2/2
+try-failover master mymaster
+selected-slave slave 172.19.0.3:6379
+promoted-slave slave 172.19.0.3:6379
+switch-master mymaster 172.19.0.2 6379 172.19.0.3 6379
```

**결과**

* Replica → Master 승격 성공
* Failover 정상 완료

---

## 6. 최종 상태 검증

### 6.1 Sentinel 기준 Master 확인

```bash
redis-cli -p 26379 sentinel get-master-addr-by-name mymaster
```

결과:

```text
172.19.0.3
6379
```

---

### 6.2 Redis Master 역할 확인

```bash
docker exec -it redis-replica redis-cli -a yourpassword info replication
```

결과:

```text
role:master
connected_slaves:0
```

---

## 7. 주의사항 및 정리

* Sentinel 포트(26379)는 데이터 노드가 아님
* `info replication`은 반드시 Redis 서버(6379)에 실행
* `redis-cli` 기본 접속 대상은 localhost:6379 이므로 Docker 환경에서는 컨테이너 지정 필수
* AUTH + Sentinel + Failover 조합 정상 동작 확인 완료

---

## 8. 결론

* Redis Sentinel HA 구성 정상 검증 완료
* AUTH 포함 환경에서 자동 Failover 성공
* 실무 배포 가능한 수준의 Redis HA 아키텍처 확보

---

## 9. 확장 가능 작업

* Spring Boot (Lettuce) Sentinel 연동
* Replica 다중 구성
* 장애 복구 시 자동 slave 재편성 검증
* 운영 환경용 모니터링 연계
