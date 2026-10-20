package app.ageguessr.features.auth.ChangeUsername;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeUsernameHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChangeUsernameHandler handler;

    @Test
    void changesTheUsernameAndReturnsTheUpdatedProfile() {
        User user = User.builder().id(USER_ID).email("me@x.com").username("old").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("NewName", USER_ID)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        ProfileResponse response = handler.handle(USER_ID, new ChangeUsernameCommand("NewName"));

        assertThat(user.getUsername()).isEqualTo("NewName");
        assertThat(response.username()).isEqualTo("NewName");
    }

    @Test
    void allowsChangingOnlyTheCasingOfOnesOwnUsername() {
        User user = User.builder().id(USER_ID).username("max").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        // The repository excludes the current user, so its own "max" doesn't count.
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("Max", USER_ID)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        assertThat(handler.handle(USER_ID, new ChangeUsernameCommand("Max")).username())
                .isEqualTo("Max");
    }

    @Test
    void rejectsAUsernameTakenByAnotherUser() {
        User user = User.builder().id(USER_ID).username("old").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("Taken", USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(USER_ID, new ChangeUsernameCommand("Taken")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("USERNAME_TAKEN");

        assertThat(user.getUsername()).isEqualTo("old");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(USER_ID, new ChangeUsernameCommand("Whatever")))
                .isInstanceOf(NotFoundException.class);
    }
}
