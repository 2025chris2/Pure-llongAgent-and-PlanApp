package com.tzl.llongagent.security.user;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RedisUserDetailsService implements UserDetailsService {

    private static final String USER_KEY_PREFIX = "user:";
    private static final String USER_ID_PREFIX = "user:id:";
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUserDetailsService(RedisTemplate<String, Object> jacksonRedisTemplate) {
        this.redisTemplate = jacksonRedisTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity entity = findByUsername(username);
        if (entity == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        List<SimpleGrantedAuthority> authorities = entity.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new User(entity.getUsername(), entity.getPasswordHash(), authorities);
    }

    public UserEntity findByUsername(String username) {
        return (UserEntity) redisTemplate.opsForValue().get(USER_KEY_PREFIX + username);
    }

    public UserEntity findById(String userId) {
        String username = (String) redisTemplate.opsForValue().get(USER_ID_PREFIX + userId);
        if (username == null) {
            return null;
        }
        return findByUsername(username);
    }

    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(USER_KEY_PREFIX + username));
    }

    public void save(UserEntity user) {
        redisTemplate.opsForValue().set(USER_KEY_PREFIX + user.getUsername(), user);
        redisTemplate.opsForValue().set(USER_ID_PREFIX + user.getId(), user.getUsername());
    }

    public UserEntity createUser(String username, String passwordHash) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRoles(List.of("ROLE_USER"));
        user.setCreatedAt(System.currentTimeMillis());
        save(user);
        return user;
    }
}
