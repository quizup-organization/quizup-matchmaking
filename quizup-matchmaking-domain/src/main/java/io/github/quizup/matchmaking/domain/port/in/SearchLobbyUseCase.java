package io.github.quizup.matchmaking.domain.port.in;

import io.github.quizup.common.domain.model.search.FilterCriteria;
import io.github.quizup.common.domain.model.search.PageCriteria;
import io.github.quizup.common.domain.model.search.PageResult;
import io.github.quizup.common.domain.model.search.SortCriteria;
import io.github.quizup.matchmaking.domain.model.Lobby;
import io.github.quizup.matchmaking.domain.query.LobbyQuery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SearchLobbyUseCase {

    CompletableFuture<PageResult<Lobby>> search(LobbyQuery.SearchLobbyQuery query);

    default CompletableFuture<PageResult<Lobby>> search(List<FilterCriteria> filters,
                                                        List<SortCriteria> sorts,
                                                        PageCriteria page) {
        return search(new LobbyQuery.SearchLobbyQuery(filters, sorts, page));
    }
}

