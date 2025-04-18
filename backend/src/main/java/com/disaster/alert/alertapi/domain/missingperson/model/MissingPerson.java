package com.disaster.alert.alertapi.domain.missingperson.model;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "missing_child")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MissingPerson {

    @Id
    private Long msspsnIdntfccd;    // 실종자 식별자

    @Column(length = 50)
    private String name;

    private Integer age;

    @Column(length = 10)
    private String gender;          // sexdstnDscd

    @Column(length = 10)
    private String targetTypeCode;  // writngTrgetDscd

    @Column(length = 200)
    private String address;         // occrAdres

    @Column(length = 500)
    private String specialFeature;  // etcSpfeatr

    @Lob
    @Column(columnDefinition = "TEXT")
    private String photoBase64;     // tknphotoFile

    private Integer photoSize;      // tknphotolength
}

