package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaUserRepository : JpaRepository<UserEntity, Long>
