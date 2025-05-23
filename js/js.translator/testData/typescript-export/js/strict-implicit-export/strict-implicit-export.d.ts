declare namespace JS_TESTS {
    type Nullable<T> = T | null | undefined
    function KtSingleton<T>(): T & (abstract new() => any);
    namespace foo {
        const forth: foo.Forth;
        interface ExportedInterface {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.ExportedInterface": unique symbol;
            };
        }
        function producer(value: number): foo.NonExportedType;
        function consumer(value: foo.NonExportedType): number;
        function childProducer(value: number): foo.NotExportedChildClass;
        function childConsumer(value: foo.NotExportedChildClass): number;
        function genericChildProducer<T extends foo.NonExportedGenericType<number>>(value: T): foo.NotExportedChildGenericClass<T>;
        function genericChildConsumer<T extends foo.NonExportedGenericType<number>>(value: foo.NotExportedChildGenericClass<T>): T;
        class A implements foo.NonExportedParent.NonExportedSecond.NonExportedUsedChild {
            constructor(value: foo.NonExportedType);
            get value(): foo.NonExportedType;
            set value(value: foo.NonExportedType);
            increment<T extends foo.NonExportedType>(t: T): foo.NonExportedType;
            getNonExportedUserChild(): foo.NonExportedParent.NonExportedSecond.NonExportedUsedChild;
            readonly __doNotUseOrImplementIt: foo.NonExportedParent.NonExportedSecond.NonExportedUsedChild["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace A.$metadata$ {
            const constructor: abstract new () => A;
        }
        class B implements foo.NonExportedType {
            constructor(v: number);
            readonly __doNotUseOrImplementIt: foo.NonExportedType["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace B.$metadata$ {
            const constructor: abstract new () => B;
        }
        class C implements foo.NonExportedInterface {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NonExportedInterface["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace C.$metadata$ {
            const constructor: abstract new () => C;
        }
        class D implements foo.NonExportedInterface, foo.ExportedInterface {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NonExportedInterface["__doNotUseOrImplementIt"] & foo.ExportedInterface["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace D.$metadata$ {
            const constructor: abstract new () => D;
        }
        class E implements foo.NonExportedType, foo.ExportedInterface {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NonExportedType["__doNotUseOrImplementIt"] & foo.ExportedInterface["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace E.$metadata$ {
            const constructor: abstract new () => E;
        }
        class F extends foo.A.$metadata$.constructor implements foo.NonExportedInterface {
            constructor();
            readonly __doNotUseOrImplementIt: foo.A["__doNotUseOrImplementIt"] & foo.NonExportedInterface["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace F.$metadata$ {
            const constructor: abstract new () => F;
        }
        class G implements foo.NonExportedGenericInterface<foo.NonExportedType> {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NonExportedGenericInterface<foo.NonExportedType>["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace G.$metadata$ {
            const constructor: abstract new () => G;
        }
        class H implements foo.NonExportedGenericType<foo.NonExportedType> {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NonExportedGenericType<foo.NonExportedType>["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace H.$metadata$ {
            const constructor: abstract new () => H;
        }
        class I implements foo.NotExportedChildClass {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NotExportedChildClass["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace I.$metadata$ {
            const constructor: abstract new () => I;
        }
        class J implements foo.NotExportedChildGenericClass<foo.NonExportedType> {
            constructor();
            readonly __doNotUseOrImplementIt: foo.NotExportedChildGenericClass<foo.NonExportedType>["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace J.$metadata$ {
            const constructor: abstract new () => J;
        }
        function baz(a: number): Promise<number>;
        function bazVoid(a: number): Promise<void>;
        function bar(): Error;
        function pep<T extends foo.NonExportedInterface & foo.NonExportedGenericInterface<number>>(x: T): void;
        const console: Console;
        const error: WebAssembly.CompileError;
        interface IA {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.IA": unique symbol;
            };
        }
        class Third extends /* foo.Second */ foo.First.$metadata$.constructor {
            constructor();
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace Third.$metadata$ {
            const constructor: abstract new () => Third;
        }
        class Sixth extends /* foo.Fifth */ foo.Third.$metadata$.constructor implements foo.Forth, foo.IC {
            constructor();
            readonly __doNotUseOrImplementIt: foo.Forth["__doNotUseOrImplementIt"] & foo.IC["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace Sixth.$metadata$ {
            const constructor: abstract new () => Sixth;
        }
        class First {
            constructor();
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace First.$metadata$ {
            const constructor: abstract new () => First;
        }
        function acceptForthLike<T extends foo.Forth>(forth: T): void;
        function acceptMoreGenericForthLike<T extends foo.IB & foo.IC & foo.Third>(forth: T): void;
        interface Service<Self extends foo.Service<Self, TEvent>, TEvent extends foo.Event<Self>> {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.Service": unique symbol;
            };
        }
        interface Event<TService extends foo.Service<TService, any /*UnknownType **/>> {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.Event": unique symbol;
            };
        }
        class SomeServiceRequest implements foo.Service<any/* foo.SomeService */, foo.Event<any/* foo.SomeService */>/* foo.SomeEvent */> {
            constructor();
            readonly __doNotUseOrImplementIt: foo.Service<any/* foo.SomeService */, foo.Event<any/* foo.SomeService */>/* foo.SomeEvent */>["__doNotUseOrImplementIt"];
        }
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace SomeServiceRequest.$metadata$ {
            const constructor: abstract new () => SomeServiceRequest;
        }
        interface NonExportedParent {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NonExportedParent": unique symbol;
            };
        }
        namespace NonExportedParent {
            interface NonExportedSecond {
                readonly __doNotUseOrImplementIt: {
                    readonly "foo.NonExportedParent.NonExportedSecond": unique symbol;
                };
            }
            namespace NonExportedSecond {
                interface NonExportedUsedChild {
                    readonly __doNotUseOrImplementIt: {
                        readonly "foo.NonExportedParent.NonExportedSecond.NonExportedUsedChild": unique symbol;
                    };
                }
            }
        }
        interface NonExportedInterface {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NonExportedInterface": unique symbol;
            };
        }
        interface NonExportedGenericInterface<T> {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NonExportedGenericInterface": unique symbol;
            };
        }
        interface NonExportedType {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NonExportedType": unique symbol;
            };
        }
        interface NonExportedGenericType<T> {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NonExportedGenericType": unique symbol;
            };
        }
        interface NotExportedChildClass extends foo.NonExportedInterface, foo.NonExportedType {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NotExportedChildClass": unique symbol;
            } & foo.NonExportedInterface["__doNotUseOrImplementIt"] & foo.NonExportedType["__doNotUseOrImplementIt"];
        }
        interface NotExportedChildGenericClass<T> extends foo.NonExportedInterface, foo.NonExportedGenericInterface<T>, foo.NonExportedGenericType<T> {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.NotExportedChildGenericClass": unique symbol;
            } & foo.NonExportedInterface["__doNotUseOrImplementIt"] & foo.NonExportedGenericInterface<T>["__doNotUseOrImplementIt"] & foo.NonExportedGenericType<T>["__doNotUseOrImplementIt"];
        }
        interface IB extends foo.IA {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.IB": unique symbol;
            } & foo.IA["__doNotUseOrImplementIt"];
        }
        interface IC extends foo.IB {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.IC": unique symbol;
            } & foo.IB["__doNotUseOrImplementIt"];
        }
        interface Forth extends foo.Third, foo.IB, foo.IC {
            readonly __doNotUseOrImplementIt: {
                readonly "foo.Forth": unique symbol;
            } & foo.IB["__doNotUseOrImplementIt"] & foo.IC["__doNotUseOrImplementIt"];
        }
    }
}
