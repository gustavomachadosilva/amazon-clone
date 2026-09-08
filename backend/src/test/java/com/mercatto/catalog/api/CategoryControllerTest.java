package com.mercatto.catalog.api;

import com.mercatto.catalog.service.ProductService;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private TokenService tokenService;

    @Test
    void listReturnsCategoriesFromService() throws Exception {
        when(productService.listCategories()).thenReturn(List.of("eletronicos", "livros"));

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("eletronicos"))
                .andExpect(jsonPath("$[1]").value("livros"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listReturnsEmptyArrayWhenNoCategories() throws Exception {
        when(productService.listCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listWithoutPrincipalStillReturns200() throws Exception {
        when(productService.listCategories()).thenReturn(List.of("eletronicos"));

        mockMvc.perform(get("/api/catalog/categories"))
                .andExpect(status().isOk());
    }
}
