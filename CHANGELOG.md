# Changelog

## [0.3.0](https://github.com/jplanckeel/Tribbae/compare/v0.2.0...v0.3.0) (2026-10-31)


### Features

* **android:** let gemini create ideas and folders through app functions ([#74](https://github.com/jplanckeel/Tribbae/issues/74)) ([b34b4f1](https://github.com/jplanckeel/Tribbae/commit/b34b4f1e4319f3f245d6406223d39928f150bae8))
* group shopping list quantities and bring every recipe into it ([#80](https://github.com/jplanckeel/Tribbae/issues/80)) ([7bb871f](https://github.com/jplanckeel/Tribbae/commit/7bb871fa061e4fb209910f4b6243a1a6b87f7754))
* pick dates with a wheel picker on web and android ([#81](https://github.com/jplanckeel/Tribbae/issues/81)) ([2beff6c](https://github.com/jplanckeel/Tribbae/commit/2beff6ced8159cd8e01475c771170c9ba24facd6))


### Bug Fixes

* show the current tipi logo on the welcome screen ([#75](https://github.com/jplanckeel/Tribbae/issues/75)) ([c00d448](https://github.com/jplanckeel/Tribbae/commit/c00d448235525eab35a9a7662bff32ad571e4b8d))


### Documentation

* update the readme to the current state of the project ([#77](https://github.com/jplanckeel/Tribbae/issues/77)) ([4c3478e](https://github.com/jplanckeel/Tribbae/commit/4c3478e30360776b43b4c7de8de67e8e771e4da8))

## [0.2.0](https://github.com/jplanckeel/Tribbae/compare/v0.1.0...v0.2.0) (2026-10-28)


### Features

* **folder:** email accounts added as collaborators of a folder ([#64](https://github.com/jplanckeel/Tribbae/issues/64)) ([4c471ce](https://github.com/jplanckeel/Tribbae/commit/4c471cecc31842581c43feb89054f7e17d1758a4))
* **frontend:** open the account settings from the desktop header ([#72](https://github.com/jplanckeel/Tribbae/issues/72)) ([999f257](https://github.com/jplanckeel/Tribbae/commit/999f2573b3f3924f943176878bcb5d403e9af8d9))
* **mcp:** expose list_folders, list_links and create_link over streamable http ([#68](https://github.com/jplanckeel/Tribbae/issues/68)) ([02882a1](https://github.com/jplanckeel/Tribbae/commit/02882a19ac1ac9db506fc3f21fc599ffe2274ef2))
* **oauth:** add an oauth 2.1 authorization server for remote mcp clients ([#67](https://github.com/jplanckeel/Tribbae/issues/67)) ([b95c545](https://github.com/jplanckeel/Tribbae/commit/b95c5455d6bf70ffe565936bece734bf584b20d8))
* **oauth:** show and revoke connected apps on web and android ([#69](https://github.com/jplanckeel/Tribbae/issues/69)) ([877c425](https://github.com/jplanckeel/Tribbae/commit/877c425072f4cabc7fac488fed7b52e7a6ea1901))
* **tribe:** email the removed member and the new owner ([#63](https://github.com/jplanckeel/Tribbae/issues/63)) ([ed6da51](https://github.com/jplanckeel/Tribbae/commit/ed6da512814515dedf484bc1551837d51a5dd05d))


### Bug Fixes

* **android:** sign the release apk so it can be installed ([#73](https://github.com/jplanckeel/Tribbae/issues/73)) ([3ead509](https://github.com/jplanckeel/Tribbae/commit/3ead5092649d18ddc93507f48b0caf94ee0be77a))
* **deps:** bump golang.org/x/net to v0.62.0 ([#70](https://github.com/jplanckeel/Tribbae/issues/70)) ([95648be](https://github.com/jplanckeel/Tribbae/commit/95648be63f5b88bcbf754b9a5a4a9a032a11af16))
* **frontend:** make the upcoming panel readable in dark mode ([#71](https://github.com/jplanckeel/Tribbae/issues/71)) ([cad350e](https://github.com/jplanckeel/Tribbae/commit/cad350e81f489661b3dcd69e08a092591d052f86))

## [0.1.0](https://github.com/jplanckeel/Tribbae/compare/v0.0.2...v0.1.0) (2026-10-27)


### ⚠ BREAKING CHANGES

* **android:** prepare the app for the play store ([#42](https://github.com/jplanckeel/Tribbae/issues/42))
* **tribe:** join a tribe by accepting an email invitation ([#28](https://github.com/jplanckeel/Tribbae/issues/28))

### Features

* add a dark theme following the « veillée » palette ([#40](https://github.com/jplanckeel/Tribbae/issues/40)) ([1435b8e](https://github.com/jplanckeel/Tribbae/commit/1435b8e5e6b850b3dc413da36cef523466cd3aea))
* add an about page ([#33](https://github.com/jplanckeel/Tribbae/issues/33)) ([d7b7ff9](https://github.com/jplanckeel/Tribbae/commit/d7b7ff93b3561d7ce1f3518c71eb2475423c97d3))
* **admin:** show new ideas, new and active accounts per week or month ([#60](https://github.com/jplanckeel/Tribbae/issues/60)) ([0ee8134](https://github.com/jplanckeel/Tribbae/commit/0ee81341853c136c4258f0f3a8d8477508244a5c))
* **auth:** explain publishing limits before saving on both clients ([b378eb4](https://github.com/jplanckeel/Tribbae/commit/b378eb41e64a1ce7d5a6d85d5793c913fd2f6f85))
* **auth:** verify the email address before publishing or inviting ([#53](https://github.com/jplanckeel/Tribbae/issues/53)) ([8e402b1](https://github.com/jplanckeel/Tribbae/commit/8e402b1548c8b3b826648f8b0ab8d78374977cd6))
* **backend:** send transactional emails through brevo ([#26](https://github.com/jplanckeel/Tribbae/issues/26)) ([98cc477](https://github.com/jplanckeel/Tribbae/commit/98cc4770bd4b0f94f168ea3a6af121011d33ea7e))
* compose ai requests as a sentence and keep or drop ideas ([#39](https://github.com/jplanckeel/Tribbae/issues/39)) ([0a56b19](https://github.com/jplanckeel/Tribbae/commit/0a56b19c423d3d70b560f49ef0543c60f54539a6))
* **folder:** invite people to the circle by email and cancel pending invitations ([#59](https://github.com/jplanckeel/Tribbae/issues/59)) ([fb096b2](https://github.com/jplanckeel/Tribbae/commit/fb096b2e5968044e339765f20fecd1f6f75930a9))
* implement plusDe function for proper elision in titles and add … ([#31](https://github.com/jplanckeel/Tribbae/issues/31)) ([7a583d1](https://github.com/jplanckeel/Tribbae/commit/7a583d12f1d253c6b28b88836b63d6de224ce231))
* move the site to tribbae.fr ([#29](https://github.com/jplanckeel/Tribbae/issues/29)) ([79524d7](https://github.com/jplanckeel/Tribbae/commit/79524d71121c33f15a614be0ea4cc7653bde91dc))
* redesign the tribe page around the campfire ([#38](https://github.com/jplanckeel/Tribbae/issues/38)) ([238cc4b](https://github.com/jplanckeel/Tribbae/commit/238cc4b191672db6456f463a0abcc3bb1275d462))
* renew the session token while the app is used ([#51](https://github.com/jplanckeel/Tribbae/issues/51)) ([e4d4d12](https://github.com/jplanckeel/Tribbae/commit/e4d4d12b0979570173165bf8e4bcb13d34a566d6))
* reset a forgotten password by email ([#27](https://github.com/jplanckeel/Tribbae/issues/27)) ([170d899](https://github.com/jplanckeel/Tribbae/commit/170d89974783f3780aa4dd1838780497edd1b4bd))
* resync an idea's details from its link ([#34](https://github.com/jplanckeel/Tribbae/issues/34)) ([1c53f95](https://github.com/jplanckeel/Tribbae/commit/1c53f9533b457a6bae14ebd546af0077762b36ca))
* **seo:** clean idea titles and write tribbae-first descriptions ([#32](https://github.com/jplanckeel/Tribbae/issues/32)) ([913dc38](https://github.com/jplanckeel/Tribbae/commit/913dc3832d7e8471d8c2312c89f04d7492f87719))
* share one page header across explorer, my ideas and tribe ([#37](https://github.com/jplanckeel/Tribbae/issues/37)) ([064a130](https://github.com/jplanckeel/Tribbae/commit/064a130e2cf82c0a08925d8cac0d6cf51a4cc405))
* **tribe:** join a tribe by accepting an email invitation ([#28](https://github.com/jplanckeel/Tribbae/issues/28)) ([8acc386](https://github.com/jplanckeel/Tribbae/commit/8acc386ecb56d58cbaafa4c06cf0f6e831c11ff7))
* upload idea photos to the server ([#41](https://github.com/jplanckeel/Tribbae/issues/41)) ([b0c5887](https://github.com/jplanckeel/Tribbae/commit/b0c5887776afbe1f353f713e419c08d39fffa3f6))


### Bug Fixes

* **android:** ask for the notification permission when a reminder is set ([#44](https://github.com/jplanckeel/Tribbae/issues/44)) ([e6071a3](https://github.com/jplanckeel/Tribbae/commit/e6071a3d893526eef321162c6a25459d6b4e4510))
* **android:** keep changes made offline until the server has them ([#49](https://github.com/jplanckeel/Tribbae/issues/49)) ([2bd2b55](https://github.com/jplanckeel/Tribbae/commit/2bd2b553286299290a06eb6148a2d8702a757976))
* **android:** make eve reminders reliable and open the camera again ([#50](https://github.com/jplanckeel/Tribbae/issues/50)) ([5278262](https://github.com/jplanckeel/Tribbae/commit/527826214231b662945dc999a5f5a2b024f57146))
* **android:** prepare the app for the play store ([#42](https://github.com/jplanckeel/Tribbae/issues/42)) ([75503ba](https://github.com/jplanckeel/Tribbae/commit/75503ba39b55a28bea00ed43dc7ffe82719bf451))
* cut link preview texts to the field limits ([#36](https://github.com/jplanckeel/Tribbae/issues/36)) ([c6ba488](https://github.com/jplanckeel/Tribbae/commit/c6ba48863c3c71072098240691dbfa1dc197ebbf))
* **seo:** validate ids read from the address before rendering ([#35](https://github.com/jplanckeel/Tribbae/issues/35)) ([63bfefb](https://github.com/jplanckeel/Tribbae/commit/63bfefb4aa533936cbf2f0fed4bd5309c8bd5151))
* use the tribbae.fr contact address instead of support@tribbae.com ([#30](https://github.com/jplanckeel/Tribbae/issues/30)) ([17d4fc5](https://github.com/jplanckeel/Tribbae/commit/17d4fc50b81f92f5bc360b0082acaf512fb2b0a2))


### Performance Improvements

* **backend:** serve every list query from a matching index ([#45](https://github.com/jplanckeel/Tribbae/issues/45)) ([283d0b3](https://github.com/jplanckeel/Tribbae/commit/283d0b3c504b0d9004d707540158d2698f33079a))


### Code Refactoring

* refont design with camp fire style ([#3](https://github.com/jplanckeel/Tribbae/issues/3)) ([07a1447](https://github.com/jplanckeel/Tribbae/commit/07a14472057f991e4cd181bdc895a0157c63fb4a))
* remove dead code ([#52](https://github.com/jplanckeel/Tribbae/issues/52)) ([9c548a3](https://github.com/jplanckeel/Tribbae/commit/9c548a3429d481c47d3a9d6d3fcd4cb1bb907c87))
* remove outdated documentation files for backend API, code quality, frontend web, mobile development, and project overview ([f809037](https://github.com/jplanckeel/Tribbae/commit/f809037ac495b4a2f59970f2a132012401c77f8c))


### Documentation

* require dco sign-off on every commit ([#11](https://github.com/jplanckeel/Tribbae/issues/11)) ([3131050](https://github.com/jplanckeel/Tribbae/commit/3131050c8e5bc919fdb298b7c74933f15fca016a))


### Dependencies

* bump docker/login-action from 4.6.0 to 4.7.0 in the actions-minor group ([#58](https://github.com/jplanckeel/Tribbae/issues/58)) ([a19fcf2](https://github.com/jplanckeel/Tribbae/commit/a19fcf2fd06c196be2d11ddcd90f69f4414a20f0))
* bump gradle-wrapper from 9.8.1 to 9.8.2 in the kotlin-patch group ([#54](https://github.com/jplanckeel/Tribbae/issues/54)) ([eec674d](https://github.com/jplanckeel/Tribbae/commit/eec674d47a0065d54a991680857fb26b55a97697))
* bump node from 25-alpine to 26-alpine in the docker-major group ([#55](https://github.com/jplanckeel/Tribbae/issues/55)) ([fb8d29a](https://github.com/jplanckeel/Tribbae/commit/fb8d29aa9dfb1d35da296cfa7debb71eed101f69))
* bump org.jetbrains.kotlinx:kotlinx-serialization-json from 1.11.0 to 1.12.0 in the kotlin-minor group ([#56](https://github.com/jplanckeel/Tribbae/issues/56)) ([fb5a04a](https://github.com/jplanckeel/Tribbae/commit/fb5a04a46a88285ee695e9f22fcda4d170071ce9))
* bump the docker-minor group with 2 updates ([#57](https://github.com/jplanckeel/Tribbae/issues/57)) ([ac77eca](https://github.com/jplanckeel/Tribbae/commit/ac77eca2c5bfb1dce69e209b50ac92fd82c7e08b))
* bump the npm-minor group across 1 directory with 2 updates ([#12](https://github.com/jplanckeel/Tribbae/issues/12)) ([7823df4](https://github.com/jplanckeel/Tribbae/commit/7823df498e807ec5300b86aa17dc2f09e318337a))


### Build System

* **android:** shrink release builds with r8 ([#48](https://github.com/jplanckeel/Tribbae/issues/48)) ([aa22391](https://github.com/jplanckeel/Tribbae/commit/aa22391e6a57a8066a049c62463ac11620fa7f63))
* **deps-dev:** bump eslint from 10.12.0 to 10.13.0 in /frontend in the npm-minor group ([#8](https://github.com/jplanckeel/Tribbae/issues/8)) ([7508f5e](https://github.com/jplanckeel/Tribbae/commit/7508f5e74dca2f27125a375279bcb9ebd1f74272))
* **deps:** bump node from 24-alpine to 25-alpine in the docker-major group ([#7](https://github.com/jplanckeel/Tribbae/issues/7)) ([6341b82](https://github.com/jplanckeel/Tribbae/commit/6341b82e35919a79f85982901ba33409a1cc4c55))
* **deps:** bump the kotlin-minor group with 8 updates ([#13](https://github.com/jplanckeel/Tribbae/issues/13)) ([6da744a](https://github.com/jplanckeel/Tribbae/commit/6da744a0049f84a5a875b842d3c61d9c0065ccf3))
