import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { OrganiseScreen } from '../syllabus/organise-screen';

@Component({
  selector: 'app-organise-page',
  imports: [OrganiseScreen],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-organise-screen (close)="close()" />`,
  styles: `:host, app-organise-screen { display: block; height: 100%; }`,
})
export class OrganisePage {
  private readonly router = inject(Router);
  protected close(): void { void this.router.navigateByUrl('/syllabus'); }
}
