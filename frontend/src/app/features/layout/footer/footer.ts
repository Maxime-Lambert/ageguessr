import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  selector: 'app-footer',
  templateUrl: './footer.html',
})
export class Footer {
  protected readonly contactEmail = 'contact@ageguessr.app';
}
