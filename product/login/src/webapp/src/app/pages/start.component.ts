import {Component, OnInit} from '@angular/core';
import {LoginApiService} from '../services/LoginApi.service';
import {User} from '../model/User';
import {Observable} from 'rxjs/Observable';

@Component({
    selector: 'ck-start',
    template: require('./start.component.html')
})
export class StartComponent implements OnInit {

    user: Observable<User>;

    constructor(private api: LoginApiService) {
    }

    ngOnInit(): void {
        this.user = this.api.getUser();
    }

    logout(): void {
        this.api.logout();
    }
}
