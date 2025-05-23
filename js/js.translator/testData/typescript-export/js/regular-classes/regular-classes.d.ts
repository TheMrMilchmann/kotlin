declare namespace JS_TESTS {
    type Nullable<T> = T | null | undefined
    function KtSingleton<T>(): T & (abstract new() => any);
    namespace foo {
        class A {
            constructor();
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A.$metadata$ {
            const constructor: abstract new () => A;
        }
        class A1 {
            constructor(x: number);
            get x(): number;
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A1.$metadata$ {
            const constructor: abstract new () => A1;
        }
        class A2 {
            constructor(x: string, y: boolean);
            get x(): string;
            get y(): boolean;
            set y(value: boolean);
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A2.$metadata$ {
            const constructor: abstract new () => A2;
        }
        class A3 {
            constructor();
            get x(): number;
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A3.$metadata$ {
            const constructor: abstract new () => A3;
        }
        class A4<T> {
            constructor(value: T);
            get value(): T;
            test(): T;
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A4.$metadata$ {
            const constructor: abstract new <T>() => A4<T>;
        }
        class A5 {
            constructor();
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A5.$metadata$ {
            const constructor: abstract new () => A5;
        }
        namespace A5 {
            abstract class Companion extends KtSingleton<Companion.$metadata$.constructor>() {
                private constructor();
            }
            /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
            namespace Companion.$metadata$ {
                abstract class constructor {
                    get x(): number;
                    private constructor();
                }
            }
        }
        class A6 {
            constructor();
            then(): number;
            catch(): number;
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A6.$metadata$ {
            const constructor: abstract new () => A6;
        }
        class GenericClassWithConstraint<T extends foo.A6> {
            constructor(test: T);
            get test(): T;
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace GenericClassWithConstraint.$metadata$ {
            const constructor: abstract new <T extends foo.A6>() => GenericClassWithConstraint<T>;
        }
    }
}
