/**
 * The state for MethodsComponent.
 */
import {MethodData} from '../../model/methods/MethodData';
import {MethodsComponent} from './methods.component';
import {Method} from '../../model/methods/Method';
import {DashboardService} from '../../services/dashboard.service';

export class MethodsComponentState {
    static KEY = 'methods';

    signature: string;
    maxResults = 100;
    data: MethodData;
    errorMessage: string;
    sortColumn = MethodsComponent.SIGNATURE_COLUMN;
    sortAscending = true;
    selectedMethod: Method;

    constructor(private dashboard: DashboardService) {
    }

    private sortBy(column: string) {
        if (this.sortColumn === column) {
            this.sortAscending = !this.sortAscending;
        } else {
            this.sortColumn = column;
        }
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
        this.dashboard
            .getMethods(this.signature, this.maxResults)
            .subscribe(data => {
                this.data = data;
                this.errorMessage = undefined;
                if (this.data.methods.length === 1) {
                    this.selectMethod(this.data.methods[0]);
                } else if (this.selectedMethod) {
                    let previouslySelected = this.data.methods.find(m => m.id === this.selectedMethod.id);
                    this.selectMethod(previouslySelected);
                } else {
                    this.selectMethod(null);
                }
            }, error => {
                this.data = undefined;
                this.errorMessage = error.statusText ? error.statusText : error;
                this.selectMethod(null);
            });
    }

    selectMethod(m: Method) {
        this.selectedMethod = m;
    }

    isSelectedMethod(m: Method) {
        return this.selectedMethod && this.selectedMethod.id === m.id;
    }

}

