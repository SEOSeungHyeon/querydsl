package strudy.querydsl.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name"})
public class Member {
    @Id
    @GeneratedValue
    private Long id;

    private String userName;

    private Integer age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public Member(String userName) {
        this(userName, 0);
    }

    public Member(String userName, Integer age) {
        this(userName, age, null);
    }

    public Member(String userName, Integer age, Team team) {
        this.userName = userName;
        this.age = age;
        if (team != null) {
            changeTeam(team);
        }
    }

    private void changeTeam(Team team) {
        this.team = team;
        team.getMembers().add(this);
    }
}
