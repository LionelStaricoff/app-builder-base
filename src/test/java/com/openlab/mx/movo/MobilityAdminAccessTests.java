package com.openlab.mx.movo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MobilityAdminAccessTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminAccedeAGeoRules() throws Exception {
        mockMvc.perform(get("/admin/geo-rules")
                        .with(user("demo_admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioNoAdminEsRechazado() throws Exception {
        mockMvc.perform(get("/admin/geo-rules")
                        .with(user("demo_user").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
