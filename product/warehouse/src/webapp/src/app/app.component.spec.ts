import {AppComponent} from './app.component';
import {TestBed} from '@angular/core/testing';
import {AppModule} from './app.module';

describe('AppComponent', () => {

    let app: AppComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AppModule]
        });
        app = TestBed.createComponent(AppComponent).componentInstance;
    });

    it('app.getApiPrefix() should return value of window.CODEKVAST_API when defined', () => {
        window['CODEKVAST_API'] = 'somePrefix';
        expect(app.getApiPrefix()).toEqual('somePrefix');
    });

    it('app.getApiPrefix() should return empty string when window.CODEKVAST_API is undefined', () => {
        window['CODEKVAST_API'] = undefined;
        expect(app.getApiPrefix()).toEqual('');
    });

    it('app.getVersion() should return value of window.CODEKVAST_VERSION when defined', () => {
        window['CODEKVAST_VERSION'] = 'someVersion';
        expect(app.getVersion()).toEqual('someVersion');
    });

    it('app.getVersion() should return "unknown" if window.CODEKVAST_VERSION is undefined', () => {
        window['CODEKVAST_VERSION'] = undefined;
        expect(app.getVersion()).toEqual('dev');
    });
});
