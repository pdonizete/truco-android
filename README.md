# Truco Acessível

Jogo de truco paulista para Android, feito para ser jogado com o TalkBack e também por quem enxerga. Dá para jogar 1 contra 1 com o computador, em duplas (você e um parceiro contra dois adversários controlados pelo computador) ou com amigos pelo Bluetooth, até 4 pessoas. Não tem propaganda, não pede conta e funciona do Android 8 em diante.

## Baixar

https://github.com/pdonizete/truco-android/releases/latest/download/truco-acessivel.apk

Este link sempre baixa a versão mais nova.

## Instalar

1. Abra o link acima no celular e baixe o arquivo truco-acessivel.apk.
2. Abra o arquivo baixado. Na primeira vez, o Android pede para permitir a instalação de apps desconhecidos para o navegador ou o gerenciador de arquivos. Toque em Configurações, ative a opção e volte.
3. Toque em Instalar.

As versões novas instalam por cima da antiga, sem perder as configurações.

## Jogar com o TalkBack

- A tela de jogo tem títulos: Placar, Vira, Mesa, Suas cartas e Ações. Use o gesto de navegar por títulos para pular entre eles.
- Cada carta da sua mão é um botão com o nome dela, por exemplo "Sete de copas", "Ás de espadas, manilha" ou "Quatro de paus, zap". Toque duas vezes para jogar.
- A voz do app anuncia as jogadas, as rodadas, os pedidos de truco e o placar. Ela só fala com o leitor de tela ligado e pode ser desligada nas Configurações. Desligada, o TalkBack lê os avisos.
- Quando chega a sua vez, toca um bipe e o foco vai para a sua primeira carta. Quando pedem truco, o foco vai para os botões Aceitar, Aumentar e Correr.
- O botão Repetir fala de novo o placar, o valor da mão, a vira, a mesa e as suas cartas.
- Correr e sair pedem confirmação. O botão Sair fica no canto de cima e é o último da varredura.

## Regras

- Baralho de 40 cartas, sem 8, 9, 10 e coringas. Cada jogador recebe 3.
- Das mais fracas para as mais fortes: 4, 5, 6, 7, dama, valete, rei, ás, 2 e 3. Cartas comuns de mesmo valor empatam (a rodada cangou).
- A vira é a carta virada depois de dar as cartas. A manilha é o valor seguinte ao da vira (depois do 3 vem o 4). As manilhas ganham de qualquer carta comum. Entre elas: ouros, espadas, copas e paus. A manilha de paus é o zap.
- A mão tem até três rodadas e ganha quem fizer duas. Se a primeira cangar, leva quem ganhar a segunda. Se a segunda ou a terceira cangar, leva quem ganhou a primeira. Se as três cangarem, leva quem é mão.
- A mão vale 1 ponto. Truco leva a 3, depois seis, nove e doze. Quem corre dá ao outro o valor de antes do pedido.
- Mão de onze: o lado com 11 pontos vê as cartas e decide se joga (vale 3) ou corre (o adversário ganha 1). Não há truco.
- Mão de ferro: os dois lados com 11. As cartas são jogadas sem ver e a mão vale 1.
- Opção nas Configurações: carta coberta a partir da segunda rodada. Ela perde para qualquer carta aberta.
- Vence quem chegar a 12 pontos.

## Compilar

Precisa do JDK 17 e do Android SDK (plataforma 35). Crie o arquivo local.properties com `sdk.dir=` apontando para o SDK e rode:

    ./gradlew testDebugUnitTest assembleDebug

O APK fica em app/build/outputs/apk/debug/app-debug.apk. Os testes também gravam prévias dos sons em sons-previa/. Os sons são sintetizados no código, sem arquivos de áudio.

O build de release (./gradlew assembleRelease) usa a chave indicada pelas variáveis TRUCO_KEYSTORE e TRUCO_KEYSTORE_PASS. Sem elas, usa a chave de debug. Cada envio para a branch main gera uma release pelo GitHub Actions. Commits com [teste] no título (a primeira linha da mensagem) só geram o APK como artifact, sem release.

## Créditos

Inspirado no Dominó G, de geofilho (https://github.com/geofilho/dominog).
