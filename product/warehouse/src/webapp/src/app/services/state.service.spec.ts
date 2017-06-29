import {StateService} from './state.service';

let stateService: StateService;

describe('StateService', () => {

    beforeEach(() => {
        stateService = new StateService();
        stateService.setLoggedOut();
    });

    it('Should be in demo mode by default', () => {
        expect(stateService.isDemoMode()).toBe(true);
    });

    it('Should respect setDemoMode(false)', () => {
        stateService.setDemoMode(false);
        expect(stateService.isDemoMode()).toBe(false);
    });

    it('Should say "Demo mode" when in demo mode', () => {
        expect(stateService.getLoginStateString()).toBe('Demo mode');
        expect(stateService.getLoginState()).toBe(null);
    });

    it('Should say "Not logged in" when not in demo mode and not logged in', () => {
        stateService.setDemoMode(false);
        expect(stateService.getLoginStateString()).toBe('Not logged in');
        expect(stateService.getLoginState()).toBe(null);
    });

    it('Should not be logged in by default', () => {
        expect(stateService.isLoggedIn()).toBe(false);
    });

    it('Should not have any auth token by default', () => {
        expect(stateService.getAuthToken()).toBeNull();
    });

    it('Should accept replaceAuthToken when not logged in', () => {
        stateService.setDemoMode(false);
        stateService.replaceAuthToken('newToken');

        expect(stateService.isLoggedIn()).toBe(true);
        expect(stateService.getAuthToken()).toBe('newToken');
        expect(stateService.getLoginStateString()).toBe('Logged in as undefined / undefined');
        expect(stateService.getLoginState())
            .toEqual({
                email: undefined,
                source: undefined,
                sourceApp: undefined
            });
    });

    it('Should accept setLoggedInAs()', () => {
        stateService.setDemoMode(false);
        stateService.setLoggedInAs('token', 17, 'customerName', 'email', 'heroku', 'my-heroku-app');
        expect(stateService.isLoggedIn()).toBe(true);
        expect(stateService.getAuthToken()).toBe('token');
        expect(stateService.getLoginStateString()).toBe('Logged in as email / customerName');
        expect(stateService.getLoginState())
            .toEqual({
                email: 'email',
                source: 'heroku',
                sourceApp: 'my-heroku-app'
            });
    });

    it('Should accept setLoggedOut()', () => {
        stateService.setDemoMode(false);
        stateService.setLoggedInAs('token', 17, 'customerName', 'email', 'heroku', 'my-heroku-app');
        expect(stateService.isLoggedIn()).toBe(true);

        stateService.setLoggedOut();

        expect(stateService.getAuthToken()).toBeNull();
        expect(stateService.getLoginStateString()).toBe('Not logged in');
    });
});
