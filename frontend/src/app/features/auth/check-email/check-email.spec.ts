import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { CheckEmail } from './check-email';

describe('CheckEmail', () => {
  let fixture: ComponentFixture<CheckEmail>;
  let component: CheckEmail;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckEmail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ email: 'user@x.com' }) } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CheckEmail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('reads the email from the query param', () => {
    expect(component.email).toBe('user@x.com');
  });

  it('resends the verification email', () => {
    component.resend();

    httpMock.expectOne('/api/auth/resend-verification').flush(null);

    expect(component.resendSent()).toBe(true);
  });
});
