
package com.openlab.mx.share;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BaseLayoutTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void raizSinSesionRedirigeALogin() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser
	void manifestSeSirveComoJson() throws Exception {
		mockMvc.perform(get("/manifest.webmanifest"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/manifest+json"));
	}

	@Test
	void serviceWorkerEnRaizEsPublico() throws Exception {
		mockMvc.perform(get("/sw.js"))
				.andExpect(status().isOk());
	}
}