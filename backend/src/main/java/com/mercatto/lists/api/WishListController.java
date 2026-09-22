package com.mercatto.lists.api;

import com.mercatto.lists.service.WishListService;
import com.mercatto.users.service.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/lists")
@RequiredArgsConstructor
public class WishListController {

    private final WishListService wishListService;

    @GetMapping
    public List<WishListService.WishListView> listMine(Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return wishListService.listByBuyer(authenticatedUser.userId());
    }

    @PostMapping
    public WishListService.WishListView create(@Valid @RequestBody CreateListRequest request, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return wishListService.createList(authenticatedUser.userId(), request.name());
    }

    @PostMapping("/{listId}/items")
    public WishListService.AddItemResult addItem(@PathVariable Long listId,
                                                   @Valid @RequestBody AddItemRequest request,
                                                   Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return wishListService.addItem(listId, authenticatedUser.userId(), request.productId());
    }

    @DeleteMapping("/{listId}/items/{productId}")
    public WishListService.WishListView removeItem(@PathVariable Long listId, @PathVariable Long productId,
                                                     Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        return wishListService.removeItem(listId, authenticatedUser.userId(), productId);
    }

    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteList(@PathVariable Long listId, Principal principal) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) principal;
        wishListService.deleteList(listId, authenticatedUser.userId());
    }

    public record CreateListRequest(@NotBlank @Size(max = 255) String name) {}

    public record AddItemRequest(@NotNull @Positive Long productId) {}
}
