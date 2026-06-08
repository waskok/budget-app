package pk.ni.pasir_wasko_klaudiusz.controller;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pk.ni.pasir_wasko_klaudiusz.dto.*;
import pk.ni.pasir_wasko_klaudiusz.model.*;
import pk.ni.pasir_wasko_klaudiusz.repository.*;
import pk.ni.pasir_wasko_klaudiusz.service.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupAndDebtIntegrationTest {

    @Autowired GroupService groupService;
    @Autowired MembershipService membershipService;
    @Autowired DebtService debtService;
    @Autowired GroupTransactionService groupTransactionService;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired DebtRepository debtRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired WebApplicationContext webApplicationContext;

    MockMvc mockMvc;
    User owner, member1, member2, outsider;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        userRepository.deleteAll();
        owner = createUser("o", "o@o.pl");
        member1 = createUser("m1", "m1@o.pl");
        member2 = createUser("m2", "m2@o.pl");
        outsider = createUser("out", "out@o.pl");
    }

    User createUser(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword("pass");
        return userRepository.save(u);
    }

    void auth(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), u.getPassword(), Collections.emptyList())
        );
    }

    Group makeGroup() {
        GroupDTO d = new GroupDTO();
        d.setName("G");
        return groupService.createGroup(d);
    }

    Membership addMem(Group g, User u) {
        MembershipDTO d = new MembershipDTO();
        d.setGroupId(g.getId());
        d.setUserEmail(u.getEmail());
        return membershipService.addMember(d);
    }

    @Test
    void Utworzenie_Grupy_Dodaje_Wlasciciela() {
        auth(owner);
        Group g = makeGroup();
        assertTrue(groupService.getAllGroups().stream().anyMatch(x -> x.getId().equals(g.getId())));
        assertEquals(owner.getId(), membershipService.getGroupMembers(g.getId()).get(0).getUser().getId());
    }

    @Test
    void Wlasciciel_Dodaje_Czlonkow() {
        auth(owner);
        Group g = makeGroup();
        MembershipDTO d = new MembershipDTO();
        d.setGroupId(g.getId());
        d.setUserEmail(member1.getEmail());
        auth(outsider);
        assertThrows(AccessDeniedException.class, () -> membershipService.addMember(d));
        auth(owner);
        assertDoesNotThrow(() -> membershipService.addMember(d));
    }

    @Test
    void Tylko_Czlonek_Widzi_Czlonkow() {
        auth(owner);
        Group g = makeGroup();
        auth(outsider);
        assertThrows(AccessDeniedException.class, () -> membershipService.getGroupMembers(g.getId()));
        auth(owner);
        assertDoesNotThrow(() -> membershipService.getGroupMembers(g.getId()));
    }

    @Test
    void Tylko_Czlonek_Widzi_Dlugi() {
        auth(owner);
        Group g = makeGroup();
        auth(outsider);
        assertThrows(AccessDeniedException.class, () -> debtService.getGroupDebts(g.getId()));
        auth(owner);
        assertDoesNotThrow(() -> debtService.getGroupDebts(g.getId()));
    }

    @Test
    void Nowy_Czlonek_Nowe_Dlugi() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        GroupTransactionDTO t1 = new GroupTransactionDTO();
        t1.setGroupId(g.getId());
        t1.setAmount(100.0);
        t1.setType("EXPENSE");
        t1.setTitle("T1");
        groupTransactionService.addGroupTransaction(t1, owner);
        addMem(g, member2);
        GroupTransactionDTO t2 = new GroupTransactionDTO();
        t2.setGroupId(g.getId());
        t2.setAmount(90.0);
        t2.setType("EXPENSE");
        t2.setTitle("T2");
        groupTransactionService.addGroupTransaction(t2, owner);
        long m2Debts = debtRepository.findByGroupId(g.getId()).stream().filter(d -> d.getDebtor().getId().equals(member2.getId())).count();
        assertEquals(1, m2Debts);
    }

    @Test
    void Transakcja_Income_Tworzy_Dlugi() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        GroupTransactionDTO t = new GroupTransactionDTO();
        t.setGroupId(g.getId());
        t.setAmount(100.0);
        t.setType("INCOME");
        t.setTitle("Inc");
        groupTransactionService.addGroupTransaction(t, owner);
        Debt d = debtRepository.findByGroupId(g.getId()).get(0);
        assertEquals(owner.getId(), d.getDebtor().getId());
        assertEquals(member1.getId(), d.getCreditor().getId());
    }

    @Test
    void Usuniecie_Czlonka_Zachowuje_Dlugi() {
        auth(owner);
        Group g = makeGroup();
        Membership m1 = addMem(g, member1);
        GroupTransactionDTO t = new GroupTransactionDTO();
        t.setGroupId(g.getId());
        t.setAmount(100.0);
        t.setType("EXPENSE");
        t.setTitle("Exp");
        groupTransactionService.addGroupTransaction(t, owner);
        membershipService.removeMember(m1.getId());
        assertFalse(debtRepository.findByGroupId(g.getId()).isEmpty());
    }

    @Test
    void Nie_Mozna_Usunac_Wlasciciela() {
        auth(owner);
        Group g = makeGroup();
        Membership m = membershipRepository.findByGroupId(g.getId()).get(0);
        assertThrows(IllegalStateException.class, () -> membershipService.removeMember(m.getId()));
    }

    @Test
    void Tylko_Wlasciciel_Usuwa_Grupe() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        auth(member1);
        assertThrows(AccessDeniedException.class, () -> groupService.deleteGroup(g.getId()));
    }

    @Test
    void CreateDebt_Tylko_Miedzy_Czlonkami() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        DebtDTO d = new DebtDTO();
        d.setGroupId(g.getId());
        d.setAmount(50.0);
        d.setTitle("D");
        d.setDebtorId(outsider.getId());
        d.setCreditorId(owner.getId());
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(d));
        d.setDebtorId(member1.getId());
        d.setCreditorId(member1.getId());
        assertThrows(IllegalStateException.class, () -> debtService.createDebt(d));
    }

    @Test
    void CreateDebt_Uprawnienia() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        addMem(g, member2);
        DebtDTO d = new DebtDTO();
        d.setGroupId(g.getId());
        d.setAmount(50.0);
        d.setTitle("D");
        d.setDebtorId(member1.getId());
        d.setCreditorId(member2.getId());
        assertDoesNotThrow(() -> debtService.createDebt(d));
        d.setDebtorId(owner.getId());
        d.setCreditorId(member1.getId());
        auth(member2);
        assertThrows(AccessDeniedException.class, () -> debtService.createDebt(d));
        d.setDebtorId(member2.getId());
        d.setCreditorId(member1.getId());
        assertDoesNotThrow(() -> debtService.createDebt(d));
    }

    @Test
    void DeleteDebt_Uprawnienia() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        addMem(g, member2);
        User m3 = createUser("m3", "m3@o.pl");
        addMem(g, m3);
        DebtDTO d = new DebtDTO();
        d.setGroupId(g.getId());
        d.setAmount(50.0);
        d.setTitle("D");
        d.setDebtorId(member1.getId());
        d.setCreditorId(member2.getId());
        Debt d1 = debtService.createDebt(d);
        Debt d2 = debtService.createDebt(d);
        auth(m3);
        assertThrows(AccessDeniedException.class, () -> debtService.deleteDebt(d1.getId()));
        auth(member1);
        assertDoesNotThrow(() -> debtService.deleteDebt(d1.getId()));
        auth(owner);
        assertDoesNotThrow(() -> debtService.deleteDebt(d2.getId()));
    }

    @Test
    void Walidacja_GraphQL() throws Exception {
        auth(owner);
        String q = "{\"query\": \"mutation { createGroup(groupDTO: { name: \\\"\\\" }) { id } }\"}";
        mockMvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(q))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void Usuniecie_Grupy_Usuwa_Wszystko() {
        auth(owner);
        Group g = makeGroup();
        addMem(g, member1);
        GroupTransactionDTO t = new GroupTransactionDTO();
        t.setGroupId(g.getId());
        t.setAmount(100.0);
        t.setType("EXPENSE");
        t.setTitle("E");
        groupTransactionService.addGroupTransaction(t, owner);
        groupService.deleteGroup(g.getId());
        assertTrue(groupRepository.findById(g.getId()).isEmpty());
        assertTrue(debtRepository.findByGroupId(g.getId()).isEmpty());
        assertTrue(membershipRepository.findByGroupId(g.getId()).isEmpty());
    }
}