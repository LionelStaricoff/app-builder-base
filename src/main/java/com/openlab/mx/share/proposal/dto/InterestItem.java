package com.openlab.mx.share.proposal.dto;

import com.openlab.mx.share.interest.entity.InterestLevel;

/** Un interés del usuario tal como lo ve el generador (plan Task 2). */
public record InterestItem(String rubroLabel, String subrubroNombre, InterestLevel nivel) {
}
