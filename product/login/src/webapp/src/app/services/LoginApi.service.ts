import {HttpClient, HttpHeaders} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/Observable';
import {Router} from '@angular/router';
import {User} from '../model/User';

@Injectable()
export class LoginApiService {

    readonly HEADERS = new HttpHeaders().set('Content-type', 'application/json; charset=utf-8');

    constructor(private http: HttpClient, private router: Router) {
    }

    getUser(): Observable<User> {
        return this.http.get<User>('/api/user', {headers: this.HEADERS});
    }

    isAuthenticated(): Observable<boolean> {
        return this.http.get<boolean>('/api/isAuthenticated', {headers: this.HEADERS});
    }

    logout(): void {
        this.http.post('/api/logout', {})
            .finally(() => {
                // noinspection JSIgnoredPromiseFromCall
                this.router.navigateByUrl('login');
            }).subscribe();
    }

    launchDashboard(customerId: number): void {
        this.http.post<string>(`/api/launchDashboard/${customerId}`, {}, {headers: this.HEADERS})
            .do(location => console.log('[ck login] launchDashboard response=%o', location))
            .subscribe(location => window.location.href = location);
    }
}
