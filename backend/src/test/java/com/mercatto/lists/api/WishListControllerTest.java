package com.mercatto.lists.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercatto.catalog.service.ProductNotFoundException;
import com.mercatto.lists.service.WishListNotFoundException;
import com.mercatto.lists.service.WishListService;
import com.mercatto.users.domain.UserRole;
import com.mercatto.users.service.AuthenticatedUser;
import com.mercatto.users.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishListController.class)
@AutoConfigureMockMvc(addFilters = false)
class WishListControllerTest {

    private static final AuthenticatedUser BUYER = new AuthenticatedUser(10L, UserRole.BUYER);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WishListService wishListService;

    @MockBean
    private TokenService tokenService;

    private static WishListService.WishListView view() {
        return new WishListService.WishListView(1L, 10L, "Birthday", List.of(5L),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void listMine_returns200UsingUserIdFromPrincipal() throws Exception {
        when(wishListService.listByBuyer(10L)).thenReturn(List.of(view()));

        mockMvc.perform(get("/api/lists").principal(BUYER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Birthday"));

        verify(wishListService).listByBuyer(10L);
    }

    @Test
    void create_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/api/lists")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(new WishListController.CreateListRequest(""))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(wishListService);
    }

    @Test
    void create_withValidName_returns200AndCallsServiceWithBuyerIdFromPrincipal() throws Exception {
        when(wishListService.createList(10L, "Birthday")).thenReturn(view());

        mockMvc.perform(post("/api/lists")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(new WishListController.CreateListRequest("Birthday"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Birthday"));

        verify(wishListService).createList(10L, "Birthday");
    }

    @Test
    void addItem_success_returns200() throws Exception {
        WishListService.AddItemResult result = new WishListService.AddItemResult(view(), false);
        when(wishListService.addItem(1L, 10L, 5L)).thenReturn(result);

        mockMvc.perform(post("/api/lists/1/items")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(new WishListController.AddItemRequest(5L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyPresent").value(false))
                .andExpect(jsonPath("$.list.name").value("Birthday"));

        verify(wishListService).addItem(1L, 10L, 5L);
    }

    @Test
    void addItem_withUnknownProduct_returns404() throws Exception {
        when(wishListService.addItem(1L, 10L, 99L))
                .thenThrow(new ProductNotFoundException("Product not found: 99"));

        mockMvc.perform(post("/api/lists/1/items")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(new WishListController.AddItemRequest(99L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void addItem_withUnknownOrNotOwnedList_returns404() throws Exception {
        when(wishListService.addItem(99L, 10L, 5L))
                .thenThrow(new WishListNotFoundException("Wish list not found: 99"));

        mockMvc.perform(post("/api/lists/99/items")
                        .contentType("application/json")
                        .principal(BUYER)
                        .content(objectMapper.writeValueAsString(new WishListController.AddItemRequest(5L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteList_returns204() throws Exception {
        mockMvc.perform(delete("/api/lists/1").principal(BUYER))
                .andExpect(status().isNoContent());

        verify(wishListService).deleteList(1L, 10L);
    }
}
