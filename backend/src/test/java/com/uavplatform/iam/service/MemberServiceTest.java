package com.uavplatform.iam.service;

import com.uavplatform.common.exception.BizException;
import com.uavplatform.common.exception.ErrorCodes;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.iam.domain.RoleEntity;
import com.uavplatform.iam.domain.UserEntity;
import com.uavplatform.iam.dto.MemberCreateRequest;
import com.uavplatform.iam.dto.MemberVO;
import com.uavplatform.iam.mapper.RoleMapper;
import com.uavplatform.iam.mapper.UserMapper;
import com.uavplatform.common.web.TokenBlacklist;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.uavplatform.common.security.LoginUser;
import com.uavplatform.common.security.UserContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private TokenBlacklist tokenBlacklist;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(userMapper, roleMapper, passwordEncoder, tokenBlacklist);
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setWorkspaceId(1L);
        loginUser.setRoleCode("super_admin");
        loginUser.setPermissions(List.of("member:read", "member:write"));
        UserContext.set(loginUser);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void create_duplicateAccount_shouldFail() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        MemberCreateRequest req = new MemberCreateRequest();
        req.setAccount("a@test.com");
        req.setDisplayName("A");
        req.setRoleId(4L);
        req.setPassword("123456");
        BizException ex = assertThrows(BizException.class, () -> memberService.create(req));
        assertEquals(ErrorCodes.ACCOUNT_EXISTS, ex.getCode());
    }

    @Test
    void create_success() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        RoleEntity role = new RoleEntity();
        role.setId(4L);
        role.setCode("operator");
        when(roleMapper.selectById(4L)).thenReturn(role);
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity u = inv.getArgument(0);
            u.setId(99L);
            return 1;
        });

        MemberCreateRequest req = new MemberCreateRequest();
        req.setAccount("pilot@test.com");
        req.setDisplayName("作业员");
        req.setRoleId(4L);
        req.setPassword("123456");
        IdVO id = memberService.create(req);
        assertEquals(99L, id.getId());
        verify(userMapper).insert(any(UserEntity.class));
    }
}
