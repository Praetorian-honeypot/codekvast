import {AgePipe} from './age.pipe';
import {APP_BASE_HREF} from '@angular/common';
import {AppComponent} from './app.component';
import {AppRoutingModule} from './app-routing.module';
import {BrowserModule, Title} from '@angular/platform-browser';
import {FormsModule} from '@angular/forms';
import {HomeComponent} from './home.component';
import {HttpModule} from '@angular/http';
import {LOCALE_ID, NgModule} from '@angular/core';
import {MethodDetailComponent} from './method-detail.component';
import {MethodsComponent} from './methods.component';
import {NgbModule} from '@ng-bootstrap/ng-bootstrap';
import {StatusComponent} from './status.component';
import {WarehouseService} from './warehouse.service';
import {ConfigService} from './config.service';
import {StateService} from './state.service';
import {ReportsComponent} from './reports.component';
import {VoteComponent} from './vote.component';

@NgModule({
    imports: [
        AppRoutingModule, BrowserModule, FormsModule, HttpModule, NgbModule.forRoot(),
    ],
    declarations: [
        AgePipe, AppComponent, HomeComponent, MethodsComponent, MethodDetailComponent, ReportsComponent, StatusComponent, VoteComponent
    ],
    providers: [
        ConfigService,
        Title,
        WarehouseService,
        StateService,
        { provide: APP_BASE_HREF, useValue: '/'},
        { provide: LOCALE_ID, useValue: window.navigator.language},
    ],
    bootstrap: [AppComponent]
})
export class AppModule {
}
