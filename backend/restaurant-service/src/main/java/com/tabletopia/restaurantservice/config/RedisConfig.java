package com.tabletopia.restaurantservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import java.time.Duration;


/**
 * Redis 설정
 *
 * @author 김예진
 * @since 2025-10-11
 */
@Configuration
public class RedisConfig {

    /**
     * Sentinel Master 이름
     * - Sentinel이 관리하는 Redis Master의 논리적 이름
     * - 실제 host/port가 아니라 Sentinel 내부에서 사용하는 식별자
     */
    @Value("${spring.data.redis.sentinel.master}")
    private String sentinelMaster;

    /**
     * Sentinel 노드 목록
     * - "host:port,host:port,..." 형식
     * - Master 장애 시 Sentinel 쿼리를 위해 사용
     */
    @Value("${spring.data.redis.sentinel.nodes}")
    private String sentinelNodes;

    /**
     * Redis 인증 비밀번호
     * - requirepass / masterauth 값과 반드시 일치해야 함
     * - Sentinel을 통해 발견된 Master/Replica 접속 시 AUTH에 사용
     */
    @Value("${spring.data.redis.password}")
    private String redisPassword;

    /**
     * Redis Sentinel 기반 RedisConnectionFactory Bean
     *
     * <p>Spring Boot의 자동 설정을 사용하지 않고 직접 Bean으로 재정의하는 이유:</p>
     * <ul>
     *   <li>Redis Sentinel + AUTH 환경에서 인증 누락 문제를 방지하기 위함</li>
     *   <li>Lettuce 프로토콜 버전(RESP2/RESP3)을 명시적으로 제어하기 위함</li>
     *   <li>Master Failover 시 인증 포함 재연결을 보장하기 위함</li>
     * </ul>
     *
     * @return Sentinel 기반 LettuceConnectionFactory
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {

        /* =========================
         * 1. Sentinel 설정 구성
         * ========================= */

        // Sentinel Master 이름 설정
        RedisSentinelConfiguration sentinelConfig =
                new RedisSentinelConfiguration().master(sentinelMaster);

        // Sentinel 노드(host:port) 파싱 후 등록
        for (String node : sentinelNodes.split(",")) {
            String[] parts = node.trim().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            sentinelConfig.sentinel(host, port);
        }

        /**
         * Redis Master / Replica 인증 비밀번호 설정
         *
         * - Sentinel은 보통 인증이 없으므로 통과하지만
         * - Sentinel이 반환한 Master에 접속할 때 이 비밀번호가 사용됨
         * - 이 설정이 없으면 HELLO만 나가고 AUTH가 누락되어 NOAUTH 발생
         */
        sentinelConfig.setPassword(RedisPassword.of(redisPassword));

        /* =========================
         * 2. Lettuce Client 설정
         * ========================= */

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        /**
                         * Redis 명령 타임아웃
                         * - 네트워크 장애나 Failover 상황에서
                         *   무한 대기 방지
                         */
                        .commandTimeout(Duration.ofSeconds(3))
                        .shutdownTimeout(Duration.ZERO)
                        .build();

        /* =========================
         * 3. ConnectionFactory 생성
         * ========================= */

        /**
         * Sentinel + Lettuce 설정을 결합한 ConnectionFactory
         * - Master 장애 시 Sentinel을 통해 새 Master 탐지
         * - 인증 정보를 포함하여 자동 재연결 수행
         */
        return new LettuceConnectionFactory(sentinelConfig, clientConfig);
    }

  /**
   * RedisTemplate 설정
   * 테이블 선점 정보, 접속자 정보 등을 저장하기 위한 템플릿
   *
   * @author 김예진
   * @since 2025-10-11
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // ObjectMapper 설정 (LocalDateTime + 타입 정보)
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 타입 정보를 저장하도록 설정 (역직렬화 문제 해결)
    objectMapper.activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .build(),
        ObjectMapper.DefaultTyping.NON_FINAL
    );

    // Custom ObjectMapper를 사용하는 Serializer
    GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

    // Key는 String으로 직렬화
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    // Value는 JSON으로 직렬화
    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);

    return template;
  }
}
