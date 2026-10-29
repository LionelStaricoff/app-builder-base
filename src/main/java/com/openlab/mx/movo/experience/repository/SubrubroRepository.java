package com.openlab.mx.movo.experience.repository;

import com.openlab.mx.movo.experience.entity.Subrubro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubrubroRepository extends JpaRepository<Subrubro, Long> {
    List<Subrubro> findByCategoria(String categoria);
    boolean existsByNombreAndCategoria(String nombre, String categoria);
}