import { ChangeDetectionStrategy, Component, HostBinding, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AuthInfo, ConnectionInfo } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService } from '../../core/services/ConfigService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { SelectInstanceDialog } from '../../shared/dialogs/SelectInstanceDialog';
import { User } from '../../shared/User';


@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent implements OnDestroy {

  @HostBinding('class')
  componentCssClass: string;

  title = 'Yamcs';
  tag: string;
  authInfo: AuthInfo;

  connectionInfo$: Observable<ConnectionInfo | null>;
  connected$: Observable<boolean>;
  user$: Observable<User | null>;

  sidebar$: Observable<boolean>;
  showMdbItem$ = new BehaviorSubject<boolean>(false);
  showMenuToggle$: Observable<boolean>;

  userSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    router: Router,
    route: ActivatedRoute,
    private authService: AuthService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
    configService: ConfigService,
  ) {
    this.tag = configService.getTag();
    this.authInfo = configService.getAuthInfo();
    this.connected$ = yamcs.yamcsClient.connected$;
    this.connectionInfo$ = yamcs.connectionInfo$;
    this.user$ = authService.user$;

    this.userSubscription = this.user$.subscribe(user => {
      if (user) {
        this.showMdbItem$.next(user.hasSystemPrivilege('GetMissionDatabase'));
      } else {
        this.showMdbItem$.next(false);
      }
    });

    this.sidebar$ = preferenceStore.sidebar$;

    this.showMenuToggle$ = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd),
      map(evt => {
        let child = route;
        while (child.firstChild) {
          child = child.firstChild;
        }

        if (child.snapshot.data && child.snapshot.data['hasSidebar'] === false) {
          return false;
        } else {
          return true;
        }
      }),
    );
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialog, {
      width: '650px',
      panelClass: ['no-padding-dialog'],
    });
  }

  toggleSidebar() {
    if (this.preferenceStore.showSidebar()) {
      this.preferenceStore.setShowSidebar(false);
    } else {
      this.preferenceStore.setShowSidebar(true);
    }
  }

  logout() {
    this.authService.logout(true);
  }

  ngOnDestroy() {
    if (this.userSubscription) {
      this.userSubscription.unsubscribe();
    }
  }
}
