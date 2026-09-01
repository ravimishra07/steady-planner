import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TodayScreen } from '../home/today-screen';

@Component({
  selector: 'app-today-page',
  imports: [TodayScreen],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-today (editPlan)="openOrganise()" (openFocus)="openFocus()" />`,
  styles: `:host { display: block; height: 100%; } app-today { height: 100%; }`,
})
export class TodayPage {
  private readonly router = inject(Router);
  protected openOrganise(): void { void this.router.navigateByUrl('/organise'); }
  protected openFocus(): void { void this.router.navigateByUrl('/focus'); }
}
