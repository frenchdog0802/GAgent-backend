package com.gagent.repository;

import com.gagent.entity.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserContactRepository extends JpaRepository<UserContact, Integer> {
    
    List<UserContact> findByUserId(Integer userId);
    
    Optional<UserContact> findByUserIdAndEmailAddress(Integer userId, String emailAddress);
}
