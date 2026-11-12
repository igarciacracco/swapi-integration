package com.example.swapi.adapter.out.persistence;

import com.example.swapi.application.port.out.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/** Adapts {@code UserRepository} to Spring Security. Login and JWT code are unchanged. */
@Profile("postgres")
@Service
class DbUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(user -> (UserDetails) User.withUsername(user.username())
                        // Already a {bcrypt} hash.
                        .password(user.passwordHash())
                        .authorities(List.of())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
    }
}
