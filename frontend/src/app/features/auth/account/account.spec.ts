import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Auth } from '../auth';
import { Account } from './account';

const USER = { id: '1', email: 'me@x.com', username: 'Player', emailVerified: true };
const WRONG_PASSWORD = {
  body: { errorCode: 'INVALID_CURRENT_PASSWORD', message: 'nope' },
  options: { status: 403, statusText: 'Forbidden' },
};

describe('Account', () => {
  let component: Account;
  let httpMock: HttpTestingController;
  let auth: Auth;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    auth = TestBed.inject(Auth);
    auth.currentUser.set(USER);
    component = TestBed.createComponent(Account).componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('prefills the current username', () => {
    expect(component.usernameForm.getRawValue().username).toBe('Player');
  });

  it('saves a new username', () => {
    component.usernameForm.setValue({ username: 'NewName' });

    component.saveUsername();
    httpMock.expectOne('/api/auth/me/username').flush({ ...USER, username: 'NewName' });

    expect(component.usernameStatus()).toBe('saved');
    expect(auth.currentUser()?.username).toBe('NewName');
  });

  it('reports a taken username', () => {
    component.usernameForm.setValue({ username: 'Taken' });

    component.saveUsername();
    httpMock
      .expectOne('/api/auth/me/username')
      .flush(
        { errorCode: 'USERNAME_TAKEN', message: 'taken' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(component.usernameError()).toBe('This username is already taken.');
  });

  it('does not send an invalid username', () => {
    component.usernameForm.setValue({ username: 'no spaces' });

    component.saveUsername();

    httpMock.expectNone('/api/auth/me/username');
  });

  it('changes the password', () => {
    component.passwordForm.setValue({
      currentPassword: 'old-tortoise-violin',
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'new-tortoise-violin',
    });

    component.changePassword();
    httpMock.expectOne('/api/auth/me/password').flush(null);

    expect(component.passwordStatus()).toBe('saved');
    expect(component.passwordForm.getRawValue().currentPassword).toBe('');
  });

  it('explains a wrong current password, lockout included', () => {
    component.passwordForm.setValue({
      currentPassword: 'wrong',
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'new-tortoise-violin',
    });

    component.changePassword();
    httpMock.expectOne('/api/auth/me/password').flush(WRONG_PASSWORD.body, WRONG_PASSWORD.options);

    expect(component.passwordError()).toContain('Incorrect password');
    expect(component.passwordError()).toContain('locked');
  });

  it('shows a refused new password under the field', () => {
    component.passwordForm.setValue({
      currentPassword: 'old-tortoise-violin',
      newPassword: 'my-Player-password',
      confirmPassword: 'my-Player-password',
    });

    component.changePassword();
    httpMock
      .expectOne('/api/auth/me/password')
      .flush(
        { errorCode: 'PASSWORD_CONTAINS_PERSONAL_INFO', message: 'personal' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(component.passwordRefusal()).toContain('username or email');
    expect(component.passwordError()).toBeNull();
  });

  it('rejects mismatched new passwords without calling the API', () => {
    component.passwordForm.setValue({
      currentPassword: 'old-tortoise-violin',
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'different',
    });

    component.changePassword();

    httpMock.expectNone('/api/auth/me/password');
  });

  it('a deletion request only sends the confirmation email and keeps the user logged in', () => {
    component.deletionForm.setValue({ password: 'tortoise-violin-42' });

    component.requestDeletion();
    httpMock
      .expectOne('/api/auth/me/deletion')
      .flush(null, { status: 202, statusText: 'Accepted' });

    expect(component.deletionStatus()).toBe('sent');
    expect(auth.currentUser()).toEqual(USER);
  });

  it('a deletion request with the wrong password explains why', () => {
    component.deletionForm.setValue({ password: 'wrong' });

    component.requestDeletion();
    httpMock.expectOne('/api/auth/me/deletion').flush(WRONG_PASSWORD.body, WRONG_PASSWORD.options);

    expect(component.deletionStatus()).toBe('idle');
    expect(component.deletionError()).toContain('Incorrect password');
  });
});
