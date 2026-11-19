package com.artifex.backend.repository;

import com.artifex.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByFirebaseUid(String firebaseUid);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findFirstByPhoneIn(Collection<String> phones);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneIn(Collection<String> phones);
    boolean existsByPhoneInAndIdNot(Collection<String> phones, Long id);
}
