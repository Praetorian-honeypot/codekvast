import {Component} from '@angular/core';
import {WarehouseService} from './warehouse.service';
import {MethodData} from './model/MethodData';
import {AgePipe} from './age.pipe';
import {DatePipe} from '@angular/common';

@Component({
    selector: 'ck-methods',
    template: require('./methods.component.html'),
    styles: [require('./methods.component.css')],
    providers: [WarehouseService, AgePipe, DatePipe],
})
export class MethodsComponent {

    signature: string;
    maxResults = 100;
    data: MethodData;
    errorMessage: string;
    dateFormat = 'age';
    sortColumn = 'signature';
    sortAscending = true;

    constructor(private warehouse: WarehouseService) {
    }

    sortBy(column: string) {
        if (this.sortColumn === column) {
            this.sortAscending = !this.sortAscending;
        } else {
            this.sortColumn = column;
        }
        console.log(`Sorting by ${this.sortColumn}, ascending=${this.sortAscending}`);
    }

    search() {
        this.warehouse
            .getMethods(this.signature, this.maxResults)
            .subscribe(data => {
                this.data = data;
                this.errorMessage = undefined;
            }, error => {
                this.data = undefined;
                this.errorMessage = error;
            }, () => console.log('getMethods() complete'));
    }

}
