package com.matdang.seatdang.member.entitiy.Vo;


import common.storeEnum.StoreType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * 상점 정보를 가진 VO
 *
 */


@Embeddable
@Data
@NoArgsConstructor
public class StoreVo {
    private Long storeId;
    private String storeName;

    @Enumerated(EnumType.STRING)
    private StoreType storeType;

    private String storeAddress;



    // Getters and setters (optional)
}