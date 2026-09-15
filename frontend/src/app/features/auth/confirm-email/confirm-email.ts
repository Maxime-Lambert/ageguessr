import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Auth } from '../auth';

type ConfirmEmailStatus = 'loading' | 'success' | 'error';

@Component({
  imports: [RouterLink],
  selector: 'app-confirm-email',
  templateUrl: './confirm-email.html',
})
export class ConfirmEmail implements OnInit {
  private readonly auth = inject(Auth);
  private readonly route = inject(ActivatedRoute);

  readonly status = signal<ConfirmEmailStatus>('loading');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.status.set('error');
      return;
    }

    this.auth.confirmEmail(token).subscribe({
      next: () => this.status.set('success'),
      error: () => this.status.set('error'),
    });
  }
}
