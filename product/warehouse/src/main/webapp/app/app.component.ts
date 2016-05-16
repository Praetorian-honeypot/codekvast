import {Component} from 'angular2/core';
import {HTTP_PROVIDERS}    from 'angular2/http';
import {ConfigService} from './config.service';
import {MethodComponent} from './method.component';

@Component({
    selector: 'ck-app', templateUrl: 'app/app.component.html',
    providers: [HTTP_PROVIDERS, ConfigService],
    directives: [MethodComponent]
})
export class AppComponent {

    now: Date = new Date();

    constructor(private _config: ConfigService) {
    }

    apiPrefix(): String {
        return this._config.getApiPrefix();
    }

    version(): String {
        return this._config.getVersion();
    }
}
