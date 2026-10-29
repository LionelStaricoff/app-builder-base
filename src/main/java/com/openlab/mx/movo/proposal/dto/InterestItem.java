package com.openlab.mx.movo.proposal.dto;

import com.openlab.mx.movo.interest.entity.InterestLevel;

/** Un interés del usuario tal como lo ve el generador (plan Task 2). */
public record InterestItem(String rubroLabel, String subrubroNombre, InterestLevel nivel) {
}
