package study.querydsl.dto;

import lombok.Data;

@Data
public class MemberDto {
    private String userName;
    private int age;

    public MemberDto(String userName, int age) {
        this.userName = userName;
        this.age = age;
    }


}
