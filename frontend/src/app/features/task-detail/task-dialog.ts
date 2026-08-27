import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TaskOpenService } from '../../core/task-open.service';
import { Dialog } from '../../ui/dialog';
import { TaskDetail } from './task-detail';

@Component({
  selector: 'app-task-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TaskDetail],
  template: `
    <ui-dialog
      [open]="open()"
      size="xl"
      [showHeader]="false"
      [showFooter]="false"
      [flush]="true"
      [fill]="true"
      [closeOnNavigation]="false"
      title="task.detail"
      (closed)="close()"
    >
      @if (key(); as taskKey) {
        <app-task-detail [key]="taskKey" chrome="dialog" (closeRequested)="close()" />
      }
    </ui-dialog>
  `,
})
export class TaskDialog {
  private readonly taskOpen = inject(TaskOpenService);

  protected readonly key = this.taskOpen.openedKey;
  protected readonly open = computed(() => this.key() !== null);

  protected close(): void {
    this.taskOpen.close();
  }
}
