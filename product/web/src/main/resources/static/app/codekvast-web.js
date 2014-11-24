var codekvastWeb = angular.module('codekvastWeb', ['ngRoute', 'ngAria', 'ui.bootstrap'])
    .controller('MainController', ['$scope', '$location', '$templateCache', '$http', function ($scope, $location, $templateCache, $http) {

        $scope.resetData = function () {
            $scope.data = {emailAddress: null};
            $scope.isSubmitDisabled = false;
            $scope.showRegisterForm = true;
            $scope.errorMessage = undefined;
        };

        $scope.resetData();

        $scope.cleanTemplateCache = function () {
            $templateCache.removeAll();
        };

        $scope.submitEmailAddress = function () {
            if ($scope.data.emailAddress) {
                $scope.isSubmitDisabled = true;

                $http.post("/register", $scope.data)
                    .success(function () {
                        $scope.showRegisterForm = false;
                        $location.path("/page/thank-you");
                    }).error(function (data, status, headers, config, statusText) {
                        $scope.errorMessage = status === 409 ? "Duplicate email address" : statusText || "Registration failed";
                        $location.path("/page/oops");
                    });
            }
        };

        $scope.focusEmailAddress = function () {
            angular.element('#email').focus();
        };

        $scope.enableSubmitButton = function () {
            $scope.isSubmitDisabled = false;
        };

        $scope.closeThankYou = function () {
            $scope.resetData();
            $location.path("/");
        };

        $scope.hasErrorMessage = function () {
            if ($scope.errorMessage === undefined) {
                $location.path('/');
                return false;
            }
            return true;
        }
    }])

    .config(['$routeProvider', '$locationProvider', function ($routeProvider, $locationProvider) {
        $routeProvider
            .when('/page/:page*', {
                templateUrl: function (routeParams) {
                    return "p/" + routeParams.page + '.html'
                }
            })

            .otherwise({
                templateUrl: 'p/welcome.html'
            });

        $locationProvider.html5Mode(true);
    }])

    .run(['$rootScope', '$location', '$log', '$document', function ($rootScope, $location, $log, $document) {
        $rootScope.$on('$viewContentLoaded', function () {
            var title = angular.element('.window-title').text();
            $log.info("Viewing '" + $location.path() + "' ('" + title + "')");
            $document[0].title = title;
            ga('send', 'pageview', $location.path());
        });
    }]);

