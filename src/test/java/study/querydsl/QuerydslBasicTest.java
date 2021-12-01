package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(QMember.member)
                .orderBy(QMember.member.userName.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        QMember.member.count(),
                        QMember.member.age.sum(),
                        QMember.member.age.avg(),
                        QMember.member.age.max(),
                        QMember.member.age.min()
                )
                .from(QMember.member)
                .fetch();

        Tuple tuple = result.get(0);
        Assertions.assertThat(tuple.get(QMember.member.count())).isEqualTo(4);
        Assertions.assertThat(tuple.get(QMember.member.age.sum())).isEqualTo(100);
        Assertions.assertThat(tuple.get(QMember.member.age.avg())).isEqualTo(25);
        Assertions.assertThat(tuple.get(QMember.member.age.max())).isEqualTo(40);
        Assertions.assertThat(tuple.get(QMember.member.age.min())).isEqualTo(10);
    }

    /*
    팀의 이름과 각 팀의 평균 연령을 구해라라
    */
    @Test
    public void group() {
        List<Tuple> result = queryFactory
                .select(QTeam.team.name, QMember.member.age.avg())
                .from(QMember.member)
                .join(QMember.member.team, QTeam.team)
                .groupBy(QTeam.team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        Assertions.assertThat(teamA.get(QTeam.team.name)).isEqualTo("teamA");
        Assertions.assertThat(teamA.get(QMember.member.age.avg())).isEqualTo(15);

        Assertions.assertThat(teamB.get(QTeam.team.name)).isEqualTo("teamB");
        Assertions.assertThat(teamB.get(QMember.member.age.avg())).isEqualTo(35);
    }

    // 팀 A에 소속된 모든 회원
    @Test
    public void join() {
        List<Member> result = queryFactory
                .selectFrom(QMember.member)
                .leftJoin(QMember.member.team, QTeam.team)
                .where(QTeam.team.name.eq("teamA"))
                .fetch();

        assertThat(result).extracting("userName").containsExactly("member1", "member2");
    }

    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));


        // 세타 조인
        // 물론 디비가 다 알아서 최적화를 한다.
        List<Member> result = queryFactory
                .select(QMember.member)
                .from(QMember.member, QTeam.team)
                .where(QMember.member.userName.eq(QTeam.team.name))
                .fetch();

        assertThat(result)
                .extracting("userName")
                .containsExactly("teamA", "teamB");
    }

    /**
     *  예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     *  JPQL:
     *      select m, t from Member m left join m.team t on t.name = 'teamA'
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(QMember.member, QTeam.team)
                .from(QMember.member)
                .leftJoin(QMember.member.team, QTeam.team)
                .on(QTeam.team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계 없는 엔티티 외부 조인
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));


        // 세타 조인
        // 물론 디비가 다 알아서 최적화를 한다.
        List<Tuple> result = queryFactory
                .select(QMember.member, QTeam.team)
                .from(QMember.member)
                .leftJoin(QTeam.team)
                .on(QMember.member.userName.eq(QTeam.team.name))  // On절은 조인하는 데이터를 필터링하는 역할
                .where(QMember.member.userName.eq(QTeam.team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.userName.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(QMember.member)
                .join(QMember.member.team, QTeam.team).fetchJoin()
                .where(QMember.member.userName.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원을 조회
     */
    @Test
    public void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(40);
    }

    /**
     * 나이가 평균 이상인 회원을 조회
     */
    @Test
    public void subQueryGoe() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(30, 40);
    }


    /**
     * In
     */
    @Test
    public void subQueryIn() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(QMember.member)
                .where(QMember.member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(20, 30, 40);
    }

    /**
     * Select 절에 서브쿼리
     */
    @Test
    public void selectSubquery() {
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(QMember.member.userName, select(memberSub.age.avg()).from(memberSub))
                .from(QMember.member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }


    /**
     * Simple Case
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(QMember.member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(QMember.member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * Complex Case
     */
    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(QMember.member.age.between(0, 20)).then("0 ~ 20살")
                        .when(QMember.member.age.between(21, 30)).then("21 ~ 30살")
                        .otherwise("기타")
                )
                .from(QMember.member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /**
     * JPQL에서는 나가지 않는다.
     */
    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(QMember.member.userName, Expressions.constant("A"))
                .from(QMember.member)
                .fetch();


        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }


    /**
     * StringValue는 생각보다 많이 쓴다.
     * 특히 Enum을 처리할 때 많이 쓴다.
     */
    @Test
    public void concat() {
        List<String> result = queryFactory
                .select(QMember.member.userName.concat("_").concat(QMember.member.age.stringValue()))
                .from(QMember.member)
                .where(QMember.member.userName.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    /**
     * 프로젝션 대상이 하나인 경우
     */
    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(QMember.member.userName)
                .from(QMember.member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    /**
     * Tuple은 Repository 계층에서만 사용하도록 하자.
     */
    @Test
    public void tupleProjections() {
        List<Tuple> result = queryFactory
                .select(QMember.member.userName, QMember.member.age)
                .from(QMember.member)
                .fetch();

        for (Tuple tuple : result) {
            String userName = tuple.get(QMember.member.userName);
            Integer age = tuple.get(QMember.member.age);

            System.out.println("userName = " + userName);
            System.out.println("age = " + age);
        }
    }

    /**
     * JPQL에서 제공하는 new Operator 문법
     */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> resultList = em.createQuery("select new study.querydsl.dto.MemberDto(m.userName, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : resultList) {
            System.out.println("memberDto = " + memberDto);
        }
    }

}
