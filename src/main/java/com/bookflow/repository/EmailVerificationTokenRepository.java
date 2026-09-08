package com.bookflow.repository;

import com.bookflow.entity.EmailVerificationToken;
import com.bookflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Deletes any existing token for the given user via a direct JPQL DELETE.
     *
     * Using @Modifying + @Query guarantees a real DELETE statement is sent
     * to the database immediately within the current transaction, before the
     * subsequent INSERT for the new token.  Without this, Spring Data's
     * derived-query delete (deleteByUser) works through the entity lifecycle
     * and does not flush early enough, allowing the UNIQUE constraint on
     * user_id to fire when a second token is inserted for the same user.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user = :user")
    void deleteByUser(@Param("user") User user);
}
