import {ActivatedRoute, Router} from '@angular/router';
import {Component, OnInit} from '@angular/core';
import {StateService} from '../services/state.service';

@Component({
    selector: 'ck-sso',
    template: '',
})

export class SsoComponent implements OnInit {

    constructor(private route: ActivatedRoute, private router: Router, private stateService: StateService) {
    }

    ngOnInit(): void {
        let token = this.route.snapshot.params['token'];
        this.stateService.setAuthToken(token);

        let parts = token.split('\.');
        console.log('parts=%o', parts);

        // header = parts[0]
        let payload = JSON.parse(atob(parts[1]));
        console.log('payload=%o', payload);
        // signature = parts[2]

        let Boomerang = window['Boomerang'];
        if (payload.source === 'HEROKU') {
            let navData = this.route.snapshot.params['navData'];
            let args = JSON.parse(atob(navData));
            console.log('navData=%o', args);
            let app = args.app || args.appname;
            Boomerang.init({
                app: app,
                addon: 'codekvast'
            });
        } else {
            Boomerang.reset();
        }

        // noinspection JSIgnoredPromiseFromCall
        this.router.navigate(['']);
    }
}
