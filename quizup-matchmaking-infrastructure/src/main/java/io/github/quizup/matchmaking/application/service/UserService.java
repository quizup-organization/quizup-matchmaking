package io.github.quizup.matchmaking.application.service;

import io.github.quizup.identity.domain.model.User;
import io.github.quizup.identity.domain.query.UserQuery;
import io.github.quizup.matchmaking.domain.port.out.UserPort;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserPort {

    private final QueryGateway queryGateway;

    public UserService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public String findNameById(String userId) {
        return queryGateway.query(
                new UserQuery.FindUserQuery(userId),
                ResponseTypes.optionalInstanceOf(User.class)
        ).join().map(User::name).orElse(null);
    }
}
