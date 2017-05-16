import {WarehouseService} from './warehouse.service';
import {ConfigService} from './config.service';
import {StateService} from './state.service';
import {Router} from '@angular/router';

let warehouse: WarehouseService;

const configServiceMock: ConfigService = {
    getVersion() {
        return 'dev'
    },
    getApiPrefix() {
        return 'xxx'
    },
    isDemoMode() {
        return true;
    }
} as ConfigService;

const routerMock: Router = {
} as Router;

describe('WarehouseService', () => {

    beforeEach(() => {
        warehouse = new WarehouseService(null, configServiceMock, new StateService(), routerMock);
    });

    it('should construct a get methods url without parameters', () => {
        expect(warehouse.constructGetMethodsUrl(undefined, undefined)).toBe('xxx/webapp/v1/methods');
    });

    it('should construct a get methods url with only signature parameter', () => {
        expect(warehouse.constructGetMethodsUrl('sig', undefined)).toBe('xxx/webapp/v1/methods?signature=sig');
    });

    it('should construct a get methods url with signature parameter copied from IDEA with Copy Reference', () => {
        expect(warehouse.constructGetMethodsUrl('sample.app.SampleApp#run', undefined))
            .toBe('xxx/webapp/v1/methods?signature=sample.app.SampleApp.run');
    });

    it('should construct a get methods url with only blank signature parameter', () => {
        expect(warehouse.constructGetMethodsUrl(' ', undefined)).toBe('xxx/webapp/v1/methods');
    });

    it('should construct a get methods url with only maxResults parameter', () => {
        expect(warehouse.constructGetMethodsUrl(undefined, 100)).toBe('xxx/webapp/v1/methods?maxResults=100');
    });

    it('should construct a get methods url with blank signature and maxResults parameter', () => {
        expect(warehouse.constructGetMethodsUrl(' ', 100)).toBe('xxx/webapp/v1/methods?maxResults=100');
    });

    it('should construct a get methods url with both signature maxResults parameter', () => {
        expect(warehouse.constructGetMethodsUrl('sig', 100)).toBe('xxx/webapp/v1/methods?signature=sig&maxResults=100');
    });

    it('should construct a get methods url with a signature and null maxResults parameter', () => {
        expect(warehouse.constructGetMethodsUrl('sig', null)).toBe('xxx/webapp/v1/methods?signature=sig');
    });

    it('should construct a get methodById url ', () => {
        expect(warehouse.constructGetMethodByIdUrl(100)).toBe('xxx/webapp/v1/method/detail/100');
    });

});
