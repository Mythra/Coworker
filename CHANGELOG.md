## 1.1.5 (October 14th, 2018)

* Fix Java `yieldStage`, to actually allow it to commit the transaction.
* Bump Kotlin to 1.2.71

## 1.1.4 (October 9th, 2018)

* Fix runAt notifications.

## 1.1.1 (September 17th, 2018)

* Stop distributing shaded jar which causes warnings from mvn compile,
  and causes intellij to not correctly parse the dependency in certain cases.
* Bump postgresql version.

## 1.1.0 (September 14th, 2018)

* Change Input Params to Interface, to allow users to dynamically refresh values.
* Add documentation site.
* Support `handleAsynchronously` which queues a particular method.
* Bump Kotlin to v 1.2.70 (over 1.2.61)
* Fixed multiple bugs in FindHeadlessWork.
* Fixed an issue where Java classes wouldn't run in certain circumstances.

## 1.0.0 (August 23rd, 2018)

* Initial Open Sourcing of Coworker.
