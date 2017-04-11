/**
 * The state for MethodsComponent.
 */
import {MethodData} from './model/MethodData';
import {MethodsComponent} from './methods.component';
import {Method} from './model/Method';
import {WarehouseService} from './warehouse.service';

export class MethodsComponentState {
    signature: string;
    maxResults = 100;
    data: MethodData;
    errorMessage: string;
    dateFormat = 'age';
    sortColumn = MethodsComponent.SIGNATURE_COLUMN;
    sortAscending = true;
    selectedMethod: Method;

    constructor(private warehouse: WarehouseService) {
        console.log('Created MethodsComponentState')
    }

    private sortBy(column: string) {
        if (this.sortColumn === column) {
            this.sortAscending = !this.sortAscending;
        } else {
            this.sortColumn = column;
        }
        console.log(`Sorting by ${this.sortColumn}, ascending=${this.sortAscending}`);
    }

    private getHeaderIconClassesFor(column: string) {
        return {
            'fa': true,
            'fa-sort-asc': this.sortAscending,
            'fa-sort-desc': !this.sortAscending,
            'invisible': column !== this.sortColumn // avoid column width fluctuations
        };
    }

    headerIconClassesSignature() {
        return this.getHeaderIconClassesFor(MethodsComponent.SIGNATURE_COLUMN);
    }

    headerIconClassesAge() {
        return this.getHeaderIconClassesFor(MethodsComponent.AGE_COLUMN);
    }

    rowIconClasses(id: number) {
        let visible = this.selectedMethod && this.selectedMethod.id === id;
        return {
            'fa': visible,
            'fa-ellipsis-h': visible
        }
    }

    sortBySignature() {
        this.sortBy(MethodsComponent.SIGNATURE_COLUMN);
    }

    sortByAge() {
        this.sortBy(MethodsComponent.AGE_COLUMN);
    }

    sortedMethods(): Method[] {
        if (!this.data || !this.data.methods) {
            return null;
        }

        return this.data.methods.sort((m1: Method, m2: Method) => {
            let cmp = 0;
            if (this.sortColumn === MethodsComponent.SIGNATURE_COLUMN) {
                cmp = m1.signature.localeCompare(m2.signature);
            } else if (this.sortColumn === MethodsComponent.AGE_COLUMN) {
                cmp = m1.lastInvokedAtMillis - m2.lastInvokedAtMillis;
            }
            if (cmp === 0) {
                // Make sure the sorting is stable
                cmp = m1.id - m2.id;
            }
            return this.sortAscending ? cmp : -cmp;
        });
    }

    search() {
        this.warehouse
            .getMethods(this.signature, this.maxResults)
            .subscribe(data => {
                this.data = data;
                this.errorMessage = undefined;
                if (this.data.methods.length === 1) {
                    this.selectMethod(this.data.methods[0]);
                } else if (this.selectedMethod) {
                    console.log('Trying to find %o', this.selectedMethod);
                    let previouslySelected = this.data.methods.find(m => m.id === this.selectedMethod.id);
                    console.log('Previously selected: %o', previouslySelected);
                    this.selectMethod(previouslySelected);
                } else {
                    this.selectMethod(null);
                }
            }, error => {
                this.data = undefined;
                this.errorMessage = error.statusText ? error.statusText : error;
                this.selectMethod(null);
            }, () => console.log('getMethods() complete'));
    }

    selectMethod(m: Method) {
        this.selectedMethod = m;
    }

    isSelectedMethod(m: Method) {
        return this.selectedMethod && this.selectedMethod.id === m.id;
    }

}

